package py.simulador.evento;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.api.generated.model.EventoCompetenciaCreate;
import py.simulador.auditoria.AuditoriaService;
import py.simulador.catalogo.EventoCatalogoEntity;
import py.simulador.catalogo.EventoCatalogoRepository;
import py.simulador.catalogo.RubroRepository;
import py.simulador.common.BusinessValidationException;
import py.simulador.common.ResourceNotFoundException;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.llm.EventoContext;
import py.simulador.llm.NarrativaService;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;
import py.simulador.trimestre.TrimestreStateMachine;

import java.math.BigDecimal;
import java.util.List;

@Service
public class EventoService {

    private final EventoCompetenciaRepository eventoCompRepo;
    private final EventoCatalogoRepository eventoCatalogoRepo;
    private final TrimestreRepository trimestreRepo;
    private final AuditoriaService auditoria;
    private final CompetenciaRepository competenciaRepo;
    private final RubroRepository rubroRepo;
    private final NarrativaService narrativaService;

    public EventoService(EventoCompetenciaRepository eventoCompRepo,
                         EventoCatalogoRepository eventoCatalogoRepo,
                         TrimestreRepository trimestreRepo,
                         AuditoriaService auditoria,
                         CompetenciaRepository competenciaRepo,
                         RubroRepository rubroRepo,
                         NarrativaService narrativaService) {
        this.eventoCompRepo = eventoCompRepo;
        this.eventoCatalogoRepo = eventoCatalogoRepo;
        this.trimestreRepo = trimestreRepo;
        this.auditoria = auditoria;
        this.competenciaRepo = competenciaRepo;
        this.rubroRepo = rubroRepo;
        this.narrativaService = narrativaService;
    }

    @Transactional(readOnly = true)
    public List<EventoCompetenciaEntity> findByCompetenciaAndTrimestre(Long competenciaId, Long trimestreId) {
        if (trimestreId != null) {
            // Returns events active for this trimestre (including those from previous Q still in effect by duration)
            return eventoCompRepo.findActivosParaTrimestre(competenciaId, trimestreId);
        }
        return eventoCompRepo.findByCompetenciaId(competenciaId);
    }

    @Transactional(readOnly = true)
    public List<EventoCompetenciaEntity> findActivosParaTrimestre(Long competenciaId, Long trimestreId) {
        return eventoCompRepo.findActivosParaTrimestre(competenciaId, trimestreId);
    }

    @Transactional
    public EventoCompetenciaEntity create(Long competenciaId, EventoCompetenciaCreate input, Long usuarioId) {
        TrimestreEntity tri = trimestreRepo.findById(input.getTrimestreId())
                .orElseThrow(() -> new ResourceNotFoundException("Trimestre", input.getTrimestreId()));

        String estado = tri.getEstado();
        if (!TrimestreStateMachine.PENDIENTE.equals(estado)
                && !TrimestreStateMachine.ABIERTO_DECISIONES.equals(estado)) {
            throw new BusinessValidationException(
                    "Solo se pueden agregar eventos a trimestres PENDIENTE o ABIERTO_DECISIONES");
        }

        EventoCatalogoEntity catalogo = eventoCatalogoRepo.findById(input.getEventoCatalogoId())
                .orElseThrow(() -> new ResourceNotFoundException("EventoCatalogo", input.getEventoCatalogoId()));

        // Validate: event not already active (same catalogo in current Q or still active from previous Q due to duration)
        List<EventoCompetenciaEntity> activos = eventoCompRepo.findActivosParaTrimestre(
                competenciaId, input.getTrimestreId());
        boolean yaActivo = activos.stream()
                .anyMatch(e -> e.getEventoCatalogoId().equals(input.getEventoCatalogoId()));
        if (yaActivo) {
            throw new BusinessValidationException(
                    "El evento \"" + catalogo.getNombre() + "\" ya esta activo en este trimestre");
        }

        EventoCompetenciaEntity entity = new EventoCompetenciaEntity();
        entity.setCompetenciaId(competenciaId);
        entity.setTrimestreId(input.getTrimestreId());
        entity.setEventoCatalogoId(input.getEventoCatalogoId());
        entity.setOrigen("MODERADOR");
        entity.setDisparadoPorUsuarioId(usuarioId);
        entity.setMagnitudAplicada(input.getMagnitudAplicada() != null
                ? BigDecimal.valueOf(input.getMagnitudAplicada())
                : catalogo.getMagnitudDefault());
        entity.setDuracionAplicada(input.getDuracionAplicada() != null
                ? input.getDuracionAplicada().shortValue()
                : catalogo.getDuracionQ());
        entity.setJustificacion(input.getJustificacion());
        EventoCompetenciaEntity saved = eventoCompRepo.save(entity);

        // Generate AI narrative if enabled and no justification was provided
        CompetenciaEntity comp = competenciaRepo.findById(competenciaId)
                .orElseThrow(() -> new ResourceNotFoundException("Competencia", competenciaId));
        if (comp.isIaHabilitada() && (saved.getJustificacion() == null || saved.getJustificacion().isBlank())) {
            String rubroNombre = rubroRepo.findById(comp.getRubroId())
                    .map(r -> r.getNombre()).orElse("General");
            TrimestreEntity triEvt = trimestreRepo.findById(saved.getTrimestreId()).orElse(null);
            int triNum = triEvt != null ? triEvt.getNumero() : 1;
            var ctx = new EventoContext(
                    catalogo.getNombre(), catalogo.getDescripcion(),
                    catalogo.getTipoEfecto(), saved.getMagnitudAplicada(),
                    saved.getDuracionAplicada(), catalogo.getSeveridad(),
                    rubroNombre, triNum
            );
            String narrativa = narrativaService.generarNarrativa(ctx);
            saved.setJustificacion(narrativa);
            eventoCompRepo.save(saved);
        }

        auditoria.registrar(competenciaId, usuarioId, "EVENTO_DISPARADO",
                "Se disparó evento: " + catalogo.getNombre());
        return saved;
    }
}

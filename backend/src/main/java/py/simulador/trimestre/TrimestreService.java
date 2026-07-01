package py.simulador.trimestre;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import py.simulador.auditoria.AuditoriaService;
import py.simulador.bot.BotDecisionService;
import py.simulador.common.BusinessValidationException;
import py.simulador.common.ResourceNotFoundException;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.competencia.CompetenciaStateMachine;
import py.simulador.decision.DecisionEquipoRepository;
import py.simulador.equipo.EquipoEntity;
import py.simulador.equipo.EquipoRepository;
import py.simulador.llm.CoachingContext;
import py.simulador.llm.CoachingTrimestreEntity;
import py.simulador.llm.CoachingTrimestreRepository;
import py.simulador.llm.NarrativaService;
import py.simulador.motor.MotorSimulacion;
import py.simulador.resultado.RankingTrimestreRepository;
import py.simulador.resultado.ResultadoCalculoRepository;

import java.time.OffsetDateTime;
import java.util.List;

import static py.simulador.trimestre.TrimestreStateMachine.*;

@Service
public class TrimestreService {

    private static final Logger log = LoggerFactory.getLogger(TrimestreService.class);

    private final TrimestreRepository trimestreRepo;
    private final CompetenciaRepository competenciaRepo;
    private final MotorSimulacion motor;
    private final AuditoriaService auditoria;
    private final NarrativaService narrativaService;
    private final CoachingTrimestreRepository coachingRepo;
    private final EquipoRepository equipoRepo;
    private final ResultadoCalculoRepository resultadoRepo;
    private final RankingTrimestreRepository rankingRepo;
    private final DecisionEquipoRepository decisionRepo;
    private final BotDecisionService botDecisionService;

    public TrimestreService(TrimestreRepository trimestreRepo,
                            CompetenciaRepository competenciaRepo,
                            MotorSimulacion motor,
                            AuditoriaService auditoria,
                            NarrativaService narrativaService,
                            CoachingTrimestreRepository coachingRepo,
                            EquipoRepository equipoRepo,
                            ResultadoCalculoRepository resultadoRepo,
                            RankingTrimestreRepository rankingRepo,
                            DecisionEquipoRepository decisionRepo,
                            BotDecisionService botDecisionService) {
        this.trimestreRepo = trimestreRepo;
        this.competenciaRepo = competenciaRepo;
        this.motor = motor;
        this.auditoria = auditoria;
        this.narrativaService = narrativaService;
        this.coachingRepo = coachingRepo;
        this.equipoRepo = equipoRepo;
        this.resultadoRepo = resultadoRepo;
        this.rankingRepo = rankingRepo;
        this.decisionRepo = decisionRepo;
        this.botDecisionService = botDecisionService;
    }

    @Transactional(readOnly = true)
    public List<TrimestreEntity> findByCompetencia(Long competenciaId) {
        return trimestreRepo.findByCompetenciaId(competenciaId);
    }

    @Transactional(readOnly = true)
    public TrimestreEntity findById(Long id) {
        return trimestreRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trimestre", id));
    }

    @Transactional
    public TrimestreEntity abrir(Long trimestreId) {
        TrimestreEntity tri = findById(trimestreId);

        if (ABIERTO_DECISIONES.equals(tri.getEstado())) {
            return tri;
        }

        TrimestreStateMachine.validarTransicion(tri.getEstado(), ABIERTO_DECISIONES);

        CompetenciaEntity comp = competenciaRepo.findById(tri.getCompetenciaId())
                .orElseThrow(() -> new IllegalStateException("Competencia not found"));
        if (!CompetenciaStateMachine.EN_CURSO.equals(comp.getEstado())) {
            throw new BusinessValidationException(
                    "La competencia debe estar EN_CURSO para abrir trimestres");
        }

        List<TrimestreEntity> abiertos = trimestreRepo.findByCompetenciaIdAndEstado(
                tri.getCompetenciaId(), ABIERTO_DECISIONES);
        List<TrimestreEntity> procesando = trimestreRepo.findByCompetenciaIdAndEstado(
                tri.getCompetenciaId(), CERRADO_PROCESANDO);
        if (!abiertos.isEmpty() || !procesando.isEmpty()) {
            throw new BusinessValidationException(
                    "Ya existe un trimestre abierto o en procesamiento");
        }

        if (tri.getNumero() > 1) {
            TrimestreEntity previo = trimestreRepo.findByCompetenciaIdAndNumero(
                    tri.getCompetenciaId(), (short) (tri.getNumero() - 1))
                    .orElseThrow(() -> new IllegalStateException("Trimestre anterior no encontrado"));
            if (!PROCESADO.equals(previo.getEstado())) {
                throw new BusinessValidationException(
                        "El trimestre anterior (Q" + previo.getNumero() + ") debe estar PROCESADO");
            }
        }

        tri.setEstado(ABIERTO_DECISIONES);
        tri.setAperturaAt(OffsetDateTime.now());
        TrimestreEntity saved = trimestreRepo.save(tri);
        auditoria.registrar(tri.getCompetenciaId(), null, "TRIMESTRE_ABIERTO",
                "Se abrió Q" + tri.getNumero() + " para recibir decisiones");

        // Bot hook: genera decisiones automáticas para equipos tipo BOT.
        // El servicio aísla errores por bot internamente (try/catch), así que
        // un fallo aquí no aborta la apertura del trimestre.
        botDecisionService.generarDecisionesParaTrimestreAbierto(saved.getId());

        return saved;
    }

    /**
     * Cierra el trimestre y ejecuta el motor de simulación.
     * Toda la operación es at��mica: si el motor falla, la transacción
     * hace rollback completo y el trimestre queda en ABIERTO_DECISIONES.
     *
     * Idempotencia: si ya está PROCESADO, lanza TrimestreYaProcesadoException
     * para que el controller devuelva 409 con el resultado existente.
     */
    @Transactional
    public TrimestreEntity cerrar(Long trimestreId) {
        TrimestreEntity tri = findById(trimestreId);

        if (PROCESADO.equals(tri.getEstado())) {
            throw new TrimestreYaProcesadoException(tri);
        }

        TrimestreStateMachine.validarTransicion(tri.getEstado(), CERRADO_PROCESANDO);

        tri.setEstado(CERRADO_PROCESANDO);
        tri.setCierreAt(OffsetDateTime.now());
        trimestreRepo.save(tri);

        // Motor procesa: decisiones → contexto → cálculo → persistencia → ranking
        // Si falla, toda la transacción hace rollback (incluyendo el cambio de estado)
        motor.procesarTrimestre(trimestreId);

        // El motor ya marcó el trimestre como PROCESADO — recargar
        TrimestreEntity processed = findById(trimestreId);

        // Generate AI coaching if enabled
        CompetenciaEntity comp = competenciaRepo.findById(tri.getCompetenciaId())
                .orElseThrow(() -> new ResourceNotFoundException("Competencia", tri.getCompetenciaId()));
        if (comp.isIaHabilitada()) {
            generarCoachingParaEquipos(processed, comp);
        }

        auditoria.registrar(tri.getCompetenciaId(), null, "TRIMESTRE_CERRADO",
                "Se cerró Q" + tri.getNumero() + " y se procesaron resultados");
        return processed;
    }

    private void generarCoachingParaEquipos(TrimestreEntity tri, CompetenciaEntity comp) {
        List<EquipoEntity> equipos = equipoRepo.findByCompetenciaId(comp.getId());
        int totalEquipos = equipos.size();

        for (EquipoEntity equipo : equipos) {
            try {
                var resultado = resultadoRepo.findByEquipoIdAndTrimestreId(equipo.getId(), tri.getId())
                        .orElse(null);
                if (resultado == null) {
                    log.warn("No resultado_calculo found for equipo {} in trimestre {}", equipo.getId(), tri.getId());
                    continue;
                }

                var rankings = rankingRepo.findByCompetenciaIdAndTrimestreId(comp.getId(), tri.getId());
                var ranking = rankings.stream()
                        .filter(r -> r.getEquipoId().equals(equipo.getId()))
                        .findFirst()
                        .orElse(null);
                if (ranking == null) {
                    log.warn("No ranking found for equipo {} in trimestre {}", equipo.getId(), tri.getId());
                    continue;
                }

                var decision = decisionRepo.findByEquipoIdAndTrimestreId(equipo.getId(), tri.getId())
                        .orElse(null);
                if (decision == null) {
                    log.warn("No decision found for equipo {} in trimestre {}", equipo.getId(), tri.getId());
                    continue;
                }

                var ctx = new CoachingContext(
                        equipo.getNombreEmpresa(),
                        tri.getNumero(),
                        resultado.getIngresos(),
                        resultado.getCostosOperativosTotal(),
                        resultado.getUtilidadNeta(),
                        resultado.getShare(),
                        ranking.getPosicion(),
                        totalEquipos,
                        decision.getPrecioVenta(),
                        decision.getInversionMarketing(),
                        decision.getInversionId(),
                        ranking.getPipAcumulado()
                );

                String texto = narrativaService.generarCoaching(ctx);

                CoachingTrimestreEntity coaching = new CoachingTrimestreEntity();
                coaching.setTrimestreId(tri.getId());
                coaching.setEquipoId(equipo.getId());
                coaching.setTexto(texto);
                coaching.setCreatedAt(OffsetDateTime.now());
                coachingRepo.save(coaching);
            } catch (Exception e) {
                log.warn("Failed to generate coaching for equipo {}: {}", equipo.getId(), e.getMessage());
            }
        }
    }
}

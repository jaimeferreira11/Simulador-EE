package py.simulador.asistente;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.asistente.dto.AsistenteContexto;
import py.simulador.asistente.dto.OrigenRespuesta;
import py.simulador.asistente.dto.RespuestaAsistente;
import py.simulador.common.ResourceNotFoundException;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.equipo.EquipoMiembroRepository;
import py.simulador.equipo.EquipoRepository;

import java.time.OffsetDateTime;

@Service
public class AsistenteService {

    private final CompetenciaRepository competenciaRepo;
    private final AsistenteFaqProvider faqProvider;
    private final AsistenteRagProvider ragProvider;
    private final AsistenteConsultaLogRepository logRepo;
    private final py.simulador.llm.LlmProperties llmProperties;
    private final EquipoRepository equipoRepo;
    private final EquipoMiembroRepository miembroRepo;

    public AsistenteService(CompetenciaRepository competenciaRepo,
                            AsistenteFaqProvider faqProvider,
                            AsistenteRagProvider ragProvider,
                            AsistenteConsultaLogRepository logRepo,
                            py.simulador.llm.LlmProperties llmProperties,
                            EquipoRepository equipoRepo,
                            EquipoMiembroRepository miembroRepo) {
        this.competenciaRepo = competenciaRepo;
        this.faqProvider = faqProvider;
        this.ragProvider = ragProvider;
        this.logRepo = logRepo;
        this.llmProperties = llmProperties;
        this.equipoRepo = equipoRepo;
        this.miembroRepo = miembroRepo;
    }

    @Transactional
    public RespuestaAsistente responder(String codigoCompetencia, Long usuarioId, String pregunta) {
        CompetenciaEntity comp = competenciaRepo.findByCodigo(codigoCompetencia)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Competencia", "codigo", codigoCompetencia));

        AsistenteProvider provider = seleccionarProvider(comp);
        Long equipoId = resolverEquipo(comp.getId(), usuarioId);
        RespuestaAsistente respuesta = provider.responder(
                new AsistenteContexto(pregunta, comp.getId(), equipoId));

        registrarConsulta(comp.getId(), usuarioId, pregunta, respuesta);
        return respuesta;
    }

    /**
     * IA encendida en la competencia Y un provider LLM real configurado → RAG (Fase 2).
     * Si la IA está apagada, o el provider es "template" (cuyo completarPrompt devuelve "{}"),
     * se usa el FAQ determinístico (Fase 1).
     */
    private AsistenteProvider seleccionarProvider(CompetenciaEntity comp) {
        boolean providerReal = !"template".equalsIgnoreCase(llmProperties.provider());
        return (comp.isIaHabilitada() && providerReal) ? ragProvider : faqProvider;
    }

    /** Equipo del usuario en esta competencia (null si no es jugador con equipo, ej. moderador). */
    private Long resolverEquipo(Long competenciaId, Long usuarioId) {
        for (py.simulador.equipo.EquipoEntity eq : equipoRepo.findByCompetenciaId(competenciaId)) {
            if (miembroRepo.findByEquipoIdAndUsuarioId(eq.getId(), usuarioId).isPresent()) {
                return eq.getId();
            }
        }
        return null;
    }

    private void registrarConsulta(Long competenciaId, Long usuarioId,
                                   String pregunta, RespuestaAsistente respuesta) {
        AsistenteConsultaLogEntity log = new AsistenteConsultaLogEntity();
        log.setCompetenciaId(competenciaId);
        log.setUsuarioId(usuarioId);
        log.setPregunta(pregunta != null && pregunta.length() > 500
                ? pregunta.substring(0, 500) : pregunta);
        log.setHuboMatch(respuesta.origen() != OrigenRespuesta.FALLBACK);
        log.setOrigen(respuesta.origen().name());
        log.setFaqId(respuesta.faqId());
        log.setCreatedAt(OffsetDateTime.now());
        logRepo.save(log);
    }
}

package py.simulador.trimestre;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import py.simulador.api.generated.TrimestresApi;
import py.simulador.api.generated.model.RankingItem;
import py.simulador.api.generated.model.ResultadoCalculo;
import py.simulador.api.generated.model.Trimestre;
import py.simulador.api.generated.model.TrimestreProcesado;
import py.simulador.equipo.EquipoEntity;
import py.simulador.resultado.RankingTrimestreEntity;
import py.simulador.resultado.ResultadoMapper;
import py.simulador.resultado.ResultadoService;
import py.simulador.notificacion.NotificacionService;
import py.simulador.websocket.GameEventPublisher;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class TrimestreController implements TrimestresApi {

    private final TrimestreService service;
    private final TrimestreMapper mapper;
    private final ResultadoService resultadoService;
    private final ResultadoMapper resultadoMapper;
    private final GameEventPublisher eventPublisher;
    private final NotificacionService notificacionService;

    public TrimestreController(TrimestreService service, TrimestreMapper mapper,
                               ResultadoService resultadoService, ResultadoMapper resultadoMapper,
                               GameEventPublisher eventPublisher,
                               NotificacionService notificacionService) {
        this.service = service;
        this.mapper = mapper;
        this.resultadoService = resultadoService;
        this.resultadoMapper = resultadoMapper;
        this.eventPublisher = eventPublisher;
        this.notificacionService = notificacionService;
    }

    @Override
    public ResponseEntity<List<Trimestre>> competenciasCompetenciaIdTrimestresGet(Long competenciaId) {
        var dtos = service.findByCompetencia(competenciaId).stream()
                .map(mapper::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @Override
    public ResponseEntity<Trimestre> trimestresIdGet(Long id) {
        return ResponseEntity.ok(mapper.toDto(service.findById(id)));
    }

    @Override
    public ResponseEntity<Trimestre> trimestresIdAbrirPost(Long id) {
        TrimestreEntity tri = service.abrir(id);

        // WS push: trimestre.abierto
        eventPublisher.publish(tri.getCompetenciaId(), "trimestre.abierto", Map.of(
                "trimestre_id", tri.getId(),
                "numero", (int) tri.getNumero()));

        String fechaCierre = tri.getCierreAt() != null ? tri.getCierreAt().toString() : "la fecha indicada";
        notificacionService.notificarCompetencia(tri.getCompetenciaId(), "TRIMESTRE_ABIERTO",
                "Trimestre Q" + tri.getNumero() + " abierto — Ingresá tus decisiones",
                "Se abrió el trimestre Q" + tri.getNumero() + ". Tenés hasta " + fechaCierre + " para enviar las decisiones de tu equipo.",
                "URGENTE");

        return ResponseEntity.ok(mapper.toDto(tri));
    }

    /**
     * Cierra el trimestre y ejecuta el motor.
     * Idempotencia: si ya esta PROCESADO, devuelve 409 con el resultado existente.
     */
    @Override
    public ResponseEntity<TrimestreProcesado> trimestresIdCerrarPost(Long id) {
        try {
            TrimestreEntity entity = service.cerrar(id);
            TrimestreProcesado dto = enriquecerProcesado(entity);

            // WS push: trimestre.procesado con ranking top 3
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("trimestre_id", entity.getId());
            payload.put("numero", (int) entity.getNumero());
            payload.put("ranking_top3", dto.getRanking().stream()
                    .limit(3)
                    .map(r -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("posicion", r.getPosicion());
                        item.put("equipo_id", r.getEquipoId());
                        item.put("nombre_empresa", r.getNombreEmpresa());
                        item.put("pip_acumulado", r.getPipAcumulado());
                        return item;
                    }).toList());
            eventPublisher.publish(entity.getCompetenciaId(), "trimestre.procesado", payload);

            notificacionService.notificarCompetencia(entity.getCompetenciaId(), "TRIMESTRE_CERRADO",
                    "Resultados Q" + entity.getNumero() + " disponibles",
                    "El motor de simulación procesó el trimestre Q" + entity.getNumero() + ". Consultá los resultados y el ranking actualizado.",
                    "IMPORTANTE");

            return ResponseEntity.ok(dto);
        } catch (TrimestreYaProcesadoException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(enriquecerProcesado(ex.getTrimestre()));
        }
    }

    /** Construye TrimestreProcesado con resultados y ranking del motor */
    private TrimestreProcesado enriquecerProcesado(TrimestreEntity tri) {
        TrimestreProcesado dto = mapper.toProcesadoDto(tri);

        List<ResultadoCalculo> resultados = resultadoService
                .findResultadosByTrimestre(tri.getId()).stream()
                .map(resultadoMapper::toResultadoDto)
                .toList();
        dto.setResultados(resultados);

        Map<Long, EquipoEntity> equipos = resultadoService.equiposPorCompetencia(tri.getCompetenciaId());
        List<RankingItem> ranking = resultadoService
                .findRanking(tri.getCompetenciaId(), tri.getId()).stream()
                .map(r -> {
                    RankingItem item = resultadoMapper.toRankingDto(r);
                    EquipoEntity eq = equipos.get(r.getEquipoId());
                    if (eq != null) {
                        item.setNombreEmpresa(eq.getNombreEmpresa());
                        item.setCodigoColor(eq.getCodigoColor());
                    }
                    return item;
                }).toList();
        dto.setRanking(ranking);

        return dto;
    }
}

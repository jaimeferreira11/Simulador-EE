package py.simulador.evento;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import py.simulador.api.generated.EventosApi;
import py.simulador.api.generated.model.EventoCompetencia;
import py.simulador.api.generated.model.EventoCompetenciaCreate;
import py.simulador.catalogo.CatalogoMapper;
import py.simulador.catalogo.EventoCatalogoEntity;
import py.simulador.catalogo.EventoCatalogoRepository;
import py.simulador.config.SecurityUtils;
import py.simulador.notificacion.NotificacionService;
import py.simulador.websocket.GameEventPublisher;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class EventoController implements EventosApi {

    private final EventoService service;
    private final EventoMapper mapper;
    private final CatalogoMapper catalogoMapper;
    private final GameEventPublisher eventPublisher;
    private final EventoCatalogoRepository catalogoRepo;
    private final NotificacionService notificacionService;

    public EventoController(EventoService service, EventoMapper mapper,
                            CatalogoMapper catalogoMapper,
                            GameEventPublisher eventPublisher,
                            EventoCatalogoRepository catalogoRepo,
                            NotificacionService notificacionService) {
        this.service = service;
        this.mapper = mapper;
        this.catalogoMapper = catalogoMapper;
        this.eventPublisher = eventPublisher;
        this.catalogoRepo = catalogoRepo;
        this.notificacionService = notificacionService;
    }

    @Override
    public ResponseEntity<List<EventoCompetencia>> competenciasCompetenciaIdEventosGet(
            Long competenciaId, Long trimestreId) {
        var dtos = service.findByCompetenciaAndTrimestre(competenciaId, trimestreId).stream()
                .map(this::toDtoEnriched)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @Override
    public ResponseEntity<EventoCompetencia> competenciasCompetenciaIdEventosPost(
            Long competenciaId, EventoCompetenciaCreate input) {
        Long userId = SecurityUtils.getUserId();
        EventoCompetenciaEntity entity = service.create(competenciaId, input, userId);

        // WS push: evento.disparado
        catalogoRepo.findById(entity.getEventoCatalogoId()).ifPresent(cat -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("trimestre_id", entity.getTrimestreId());
            payload.put("evento_codigo", cat.getCodigo());
            payload.put("evento_nombre", cat.getNombre());
            payload.put("severidad", cat.getSeveridad());
            payload.put("duracion_q", (int) entity.getDuracionAplicada());
            String desc = entity.getJustificacion() != null
                    ? entity.getJustificacion() : cat.getDescripcion();
            payload.put("descripcion", desc);
            eventPublisher.publish(competenciaId, "evento.disparado", payload);

            String severidad = "GRAVE".equals(cat.getSeveridad()) ? "URGENTE" : "IMPORTANTE";
            notificacionService.notificarCompetencia(competenciaId, "EVENTO_DISPARADO",
                    "Nuevo evento: " + cat.getNombre(),
                    desc,
                    severidad);
        });

        return ResponseEntity.status(201).body(toDtoEnriched(entity));
    }

    private EventoCompetencia toDtoEnriched(EventoCompetenciaEntity entity) {
        EventoCompetencia dto = mapper.toDto(entity);
        catalogoRepo.findById(entity.getEventoCatalogoId()).ifPresent(cat ->
            dto.setEventoCatalogo(catalogoMapper.toEventoCatalogoDto(cat))
        );
        return dto;
    }
}

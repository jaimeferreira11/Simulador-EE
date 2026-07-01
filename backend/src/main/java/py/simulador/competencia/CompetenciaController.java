package py.simulador.competencia;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import py.simulador.api.generated.CompetenciasApi;
import py.simulador.api.generated.model.*;
import py.simulador.config.SecurityUtils;
import py.simulador.equipo.EquipoMapper;
import py.simulador.equipo.EquipoRepository;
import py.simulador.trimestre.TrimestreMapper;
import py.simulador.trimestre.TrimestreRepository;
import py.simulador.notificacion.NotificacionService;
import py.simulador.websocket.GameEventPublisher;

import java.util.Map;

@RestController
public class CompetenciaController implements CompetenciasApi {

    private final CompetenciaService service;
    private final CompetenciaMapper mapper;
    private final EquipoRepository equipoRepo;
    private final EquipoMapper equipoMapper;
    private final TrimestreRepository trimestreRepo;
    private final TrimestreMapper trimestreMapper;
    private final GameEventPublisher eventPublisher;
    private final NotificacionService notificacionService;

    public CompetenciaController(CompetenciaService service, CompetenciaMapper mapper,
                                 EquipoRepository equipoRepo, EquipoMapper equipoMapper,
                                 TrimestreRepository trimestreRepo, TrimestreMapper trimestreMapper,
                                 GameEventPublisher eventPublisher,
                                 NotificacionService notificacionService) {
        this.service = service;
        this.mapper = mapper;
        this.equipoRepo = equipoRepo;
        this.equipoMapper = equipoMapper;
        this.trimestreRepo = trimestreRepo;
        this.trimestreMapper = trimestreMapper;
        this.eventPublisher = eventPublisher;
        this.notificacionService = notificacionService;
    }

    @Override
    public ResponseEntity<PagedCompetencias> competenciasGet(Integer page, Integer size,
                                                              String estado, Long entidadId,
                                                              Integer anio) {
        Long userId = SecurityUtils.getUserId();
        String rol = SecurityUtils.getRol();
        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;

        var entities = service.findAll(estado, rol, userId, entidadId, anio, pageNum, pageSize);
        long total = service.countAll(estado, rol, userId, entidadId, anio);

        var dtos = entities.stream().map(mapper::toDto).toList();
        PagedCompetencias paged = new PagedCompetencias();
        paged.setContent(dtos);
        paged.setTotalElements(total);
        paged.setTotalPages((int) ((total + pageSize - 1) / pageSize));
        paged.setPage(pageNum);
        paged.setSize(pageSize);
        return ResponseEntity.ok(paged);
    }

    @Override
    public ResponseEntity<Competencia> competenciasPost(CompetenciaCreate input) {
        Long moderadorId = SecurityUtils.getUserId();
        CompetenciaEntity entity = service.create(input, moderadorId);
        return ResponseEntity.status(201).body(mapper.toDto(entity));
    }

    @Override
    public ResponseEntity<CompetenciaDetalle> competenciasIdGet(Long id) {
        CompetenciaEntity entity = service.findById(id);
        CompetenciaDetalle dto = mapper.toDetalleDto(entity);

        // Populate equipos
        var equipos = equipoRepo.findByCompetenciaId(id);
        dto.setEquipos(equipos.stream().map(equipoMapper::toDto).toList());

        // Populate trimestres and find trimestre_actual
        var trimestres = trimestreRepo.findByCompetenciaId(id);
        dto.setTrimestres(trimestres.stream().map(trimestreMapper::toDto).toList());

        // trimestre_actual: the one with ABIERTO_DECISIONES or CERRADO_PROCESANDO state
        trimestres.stream()
                .filter(t -> "ABIERTO_DECISIONES".equals(t.getEstado())
                        || "CERRADO_PROCESANDO".equals(t.getEstado()))
                .findFirst()
                .ifPresent(t -> dto.setTrimestreActual(trimestreMapper.toDto(t)));

        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<Competencia> competenciasIdPatch(Long id, CompetenciaUpdate input) {
        return ResponseEntity.ok(mapper.toDto(service.update(id, input)));
    }

    @Override
    public ResponseEntity<Void> competenciasIdDelete(Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CompetenciaPublica> competenciasByCodigoCodigoGet(String codigo) {
        CompetenciaEntity entity = service.findByCodigo(codigo);
        return ResponseEntity.ok(mapper.toPublicaDto(entity));
    }

    @Override
    public ResponseEntity<Competencia> competenciasIdAbrirInscripcionPost(Long id) {
        return ResponseEntity.ok(mapper.toDto(service.abrirInscripcion(id)));
    }

    @Override
    public ResponseEntity<Competencia> competenciasIdIniciarPost(Long id) {
        CompetenciaEntity comp = service.iniciar(id);
        notificacionService.notificarCompetencia(id, "COMPETENCIA_INICIADA",
                "La competencia " + comp.getNombre() + " ha comenzado!",
                "El moderador inició la competencia. Preparate para el primer trimestre.",
                "IMPORTANTE");
        return ResponseEntity.ok(mapper.toDto(comp));
    }

    @Override
    public ResponseEntity<Void> competenciasIdPausarPost(Long id) {
        CompetenciaEntity comp = service.pausar(id);

        eventPublisher.publish(comp.getId(), "competencia.estado_cambiado", Map.of(
                "estado_anterior", CompetenciaStateMachine.EN_CURSO,
                "estado_nuevo", comp.getEstado()));

        notificacionService.notificarCompetencia(id, "COMPETENCIA_PAUSADA",
                "La competencia " + comp.getNombre() + " fue pausada",
                "El moderador pausó la competencia. No se pueden enviar decisiones hasta que se reanude.",
                "INFO");

        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> competenciasIdReanudarPost(Long id) {
        CompetenciaEntity comp = service.reanudar(id);

        eventPublisher.publish(comp.getId(), "competencia.estado_cambiado", Map.of(
                "estado_anterior", CompetenciaStateMachine.PAUSADA,
                "estado_nuevo", comp.getEstado()));

        return ResponseEntity.ok().build();
    }

    @PostMapping("/competencias/{id}/finalizar")
    public ResponseEntity<Competencia> competenciasIdFinalizarPost(@PathVariable Long id) {
        CompetenciaEntity comp = service.finalizar(id);

        eventPublisher.publish(comp.getId(), "competencia.estado_cambiado", Map.of(
                "estado_nuevo", comp.getEstado()));

        notificacionService.notificarCompetencia(id, "COMPETENCIA_FINALIZADA",
                "La competencia " + comp.getNombre() + " ha finalizado",
                "La competencia terminó. Consultá el ranking final y descargá el informe.",
                "IMPORTANTE");

        return ResponseEntity.ok(mapper.toDto(comp));
    }

    @PostMapping("/competencias/{id}/archivar")
    public ResponseEntity<Competencia> competenciasIdArchivarPost(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toDto(service.archivar(id)));
    }
}

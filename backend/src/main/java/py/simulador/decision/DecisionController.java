package py.simulador.decision;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import py.simulador.api.generated.DecisionesApi;
import py.simulador.api.generated.model.Decision;
import py.simulador.api.generated.model.DecisionInput;
import py.simulador.api.generated.model.ValidacionDecision;
import py.simulador.api.generated.model.ValidacionDecisionErroresInner;
import py.simulador.bot.BotDecisionService;
import py.simulador.config.SecurityUtils;
import py.simulador.equipo.EquipoEntity;
import py.simulador.equipo.EquipoRepository;
import py.simulador.notificacion.NotificacionService;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;
import py.simulador.usuario.UsuarioEntity;
import py.simulador.usuario.UsuarioRepository;
import py.simulador.websocket.GameEventPublisher;

import java.util.List;
import java.util.Map;

@RestController
@Validated
public class DecisionController implements DecisionesApi {

    private final DecisionService service;
    private final DecisionMapper mapper;
    private final GameEventPublisher eventPublisher;
    private final TrimestreRepository trimestreRepo;
    private final ContextoDecisionService contextoService;
    private final NotificacionService notificacionService;
    private final EquipoRepository equipoRepo;
    private final UsuarioRepository usuarioRepo;
    private final BotDecisionService botDecisionService;

    public DecisionController(DecisionService service, DecisionMapper mapper,
                              GameEventPublisher eventPublisher, TrimestreRepository trimestreRepo,
                              ContextoDecisionService contextoService,
                              NotificacionService notificacionService,
                              EquipoRepository equipoRepo,
                              UsuarioRepository usuarioRepo,
                              BotDecisionService botDecisionService) {
        this.service = service;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
        this.trimestreRepo = trimestreRepo;
        this.contextoService = contextoService;
        this.notificacionService = notificacionService;
        this.equipoRepo = equipoRepo;
        this.usuarioRepo = usuarioRepo;
        this.botDecisionService = botDecisionService;
    }

    @Override
    public ResponseEntity<Decision> equiposEquipoIdTrimestresTrimestreIdDecisionGet(
            Long equipoId, Long trimestreId) {
        return ResponseEntity.ok(mapper.toDto(service.findByEquipoAndTrimestre(equipoId, trimestreId)));
    }

    @Override
    public ResponseEntity<Decision> equiposEquipoIdTrimestresTrimestreIdDecisionPut(
            Long equipoId, Long trimestreId, DecisionInput input) {
        Long userId = SecurityUtils.getUserId();
        return ResponseEntity.ok(mapper.toDto(service.upsert(equipoId, trimestreId, input, userId)));
    }

    @Override
    public ResponseEntity<ValidacionDecision> equiposEquipoIdTrimestresTrimestreIdDecisionValidarPost(
            Long equipoId, Long trimestreId, DecisionInput input) {
        List<String> errores = service.validar(equipoId, trimestreId, input);
        ValidacionDecision dto = new ValidacionDecision();
        dto.setEsValida(errores.isEmpty());
        dto.setErrores(errores.stream().map(msg -> {
            ValidacionDecisionErroresInner e = new ValidacionDecisionErroresInner();
            e.setMensaje(msg);
            return e;
        }).toList());
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<Decision> equiposEquipoIdTrimestresTrimestreIdDecisionEnviarPost(
            Long equipoId, Long trimestreId) {
        Long userId = SecurityUtils.getUserId();
        DecisionEquipoEntity decision = service.enviar(equipoId, trimestreId, userId);

        // WS push: decision.recibida (broadcast a toda la competencia)
        TrimestreEntity tri = trimestreRepo.findById(trimestreId).orElse(null);
        if (tri != null) {
            eventPublisher.publish(tri.getCompetenciaId(), "decision.recibida", Map.of(
                    "equipo_id", equipoId,
                    "trimestre_id", trimestreId,
                    "estado", decision.getEstado()));

            String nombreUsuario = usuarioRepo.findById(userId)
                    .map(UsuarioEntity::getNombreCompleto).orElse("Un miembro");
            notificacionService.notificarCompetencia(tri.getCompetenciaId(), "DECISION_ENVIADA",
                    "Tu equipo envió las decisiones del Q" + tri.getNumero(),
                    "Las decisiones del trimestre Q" + tri.getNumero() + " fueron enviadas exitosamente por " + nombreUsuario + ".",
                    "INFO");
        }

        return ResponseEntity.ok(mapper.toDto(decision));
    }

    @Override
    public ResponseEntity<Decision> equiposEquipoIdTrimestresTrimestreIdDecisionReabrirPost(
            Long equipoId, Long trimestreId) {
        DecisionEquipoEntity decision = service.reabrir(equipoId, trimestreId);

        TrimestreEntity tri = trimestreRepo.findById(trimestreId).orElse(null);
        if (tri != null) {
            // Emit decision.reabierta WS event to the team
            eventPublisher.publishToEquipo(tri.getCompetenciaId(), equipoId,
                    "decision.reabierta", Map.of(
                            "equipo_id", equipoId,
                            "trimestre_id", trimestreId,
                            "trimestre_numero", (int) tri.getNumero()));

            notificacionService.notificarCompetencia(tri.getCompetenciaId(), "DECISION_REABIERTA",
                    "Las decisiones del Q" + tri.getNumero() + " fueron reabiertas",
                    "El moderador reabrió las decisiones del trimestre Q" + tri.getNumero() + ". Podés modificarlas y reenviar.",
                    "URGENTE");
        }

        return ResponseEntity.ok(mapper.toDto(decision));
    }

    @Override
    public ResponseEntity<Decision> regenerarDecisionBot(Long equipoId, Long trimestreId) {
        Long userId = SecurityUtils.getUserId();
        DecisionEquipoEntity decision = botDecisionService.regenerarDecisionDeBot(
                equipoId, trimestreId, userId);

        // Notify the competencia so the moderador UI updates the decisions table.
        TrimestreEntity tri = trimestreRepo.findById(trimestreId).orElse(null);
        if (tri != null) {
            eventPublisher.publish(tri.getCompetenciaId(), "decision.recibida", Map.of(
                    "equipo_id", equipoId,
                    "trimestre_id", trimestreId,
                    "estado", decision.getEstado()));
        }

        return ResponseEntity.ok(mapper.toDto(decision));
    }

    @Override
    public ResponseEntity<List<Decision>> trimestresTrimestreIdDecisionesGet(Long trimestreId) {
        var dtos = service.findByTrimestre(trimestreId).stream()
                .map(mapper::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/equipos/{equipoId}/trimestres/{trimestreId}/decision/contexto")
    public ResponseEntity<ContextoDecisionDTO> getContextoDecision(
            @PathVariable @Positive Long equipoId, @PathVariable @Positive Long trimestreId) {
        return ResponseEntity.ok(contextoService.buildContexto(equipoId, trimestreId));
    }

    @PostMapping("/equipos/{equipoId}/trimestres/{trimestreId}/decision/proyeccion")
    public ResponseEntity<ProyeccionFinancieraDTO> getProyeccion(
            @PathVariable @Positive Long equipoId, @PathVariable @Positive Long trimestreId,
            @Valid @RequestBody DecisionInput input) {
        return ResponseEntity.ok(contextoService.calcularProyeccion(equipoId, trimestreId, input));
    }
}

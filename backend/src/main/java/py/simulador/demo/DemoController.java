package py.simulador.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaMapper;
import py.simulador.decision.DecisionEquipoEntity;
import py.simulador.decision.DecisionMapper;
import py.simulador.websocket.GameEventPublisher;

import java.util.Map;

@RestController
@RequestMapping("/competencias/{id}/demo")
@PreAuthorize("hasRole('MODERADOR') or hasRole('ADMIN_PLATAFORMA')")
public class DemoController {

    private final DemoService demoService;
    private final CompetenciaMapper compMapper;
    private final DecisionMapper decisionMapper;
    private final GameEventPublisher events;

    public DemoController(DemoService demoService,
                          CompetenciaMapper compMapper,
                          DecisionMapper decisionMapper,
                          GameEventPublisher events) {
        this.demoService = demoService;
        this.compMapper = compMapper;
        this.decisionMapper = decisionMapper;
        this.events = events;
    }

    @PostMapping("/reiniciar")
    public ResponseEntity<?> reiniciar(@PathVariable Long id) {
        CompetenciaEntity comp = demoService.reiniciar(id);
        events.publish(id, "competencia.reiniciada", Map.of("competenciaId", id));
        return ResponseEntity.ok(compMapper.toDto(comp));
    }

    @PostMapping("/decision-ceo")
    public ResponseEntity<?> decisionCeo(@PathVariable Long id,
                                         @RequestBody Map<String, Object> payload) {
        DecisionEquipoEntity dec = demoService.decisionCeo(id, payload);
        return ResponseEntity.ok(decisionMapper.toDto(dec));
    }

    @PostMapping("/avanzar")
    public ResponseEntity<AvanzarResult> avanzar(@PathVariable Long id) {
        return ResponseEntity.ok(demoService.avanzar(id));
    }
}

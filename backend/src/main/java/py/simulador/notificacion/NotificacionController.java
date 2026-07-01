package py.simulador.notificacion;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import py.simulador.config.SecurityUtils;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notificaciones")
public class NotificacionController {

    private final NotificacionService service;

    public NotificacionController(NotificacionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listar(
            @RequestParam(required = false) Boolean leida,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = SecurityUtils.getUserId();
        int safeSize = Math.min(size, 50);

        List<NotificacionEntity> items = service.listar(userId, leida, page, safeSize);
        long total = service.contarTotal(userId, leida);
        int totalPages = (int) Math.ceil((double) total / safeSize);

        List<Map<String, Object>> content = items.stream().map(this::toDto).toList();

        return ResponseEntity.ok(Map.of(
                "content", content,
                "page", page,
                "size", safeSize,
                "totalElements", total,
                "totalPages", totalPages
        ));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> count(
            @RequestParam(required = false) Boolean leida) {
        Long userId = SecurityUtils.getUserId();
        long count = service.contar(userId, leida);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/{id}/leer")
    public ResponseEntity<Void> marcarLeida(@PathVariable Long id) {
        Long userId = SecurityUtils.getUserId();
        service.marcarLeida(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/leer-todas")
    public ResponseEntity<Void> marcarTodasLeidas() {
        Long userId = SecurityUtils.getUserId();
        service.marcarTodasLeidas(userId);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toDto(NotificacionEntity e) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id", e.getId());
        map.put("tipo", e.getTipo());
        map.put("titulo", e.getTitulo());
        map.put("descripcion", e.getDescripcion());
        map.put("severidad", e.getSeveridad());
        map.put("leida", e.isLeida());
        map.put("created_at", e.getCreatedAt());
        map.put("competencia_id", e.getCompetenciaId());
        return map;
    }
}

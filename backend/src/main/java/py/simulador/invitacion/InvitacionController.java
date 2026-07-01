package py.simulador.invitacion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import py.simulador.config.SecurityUtils;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class InvitacionController {

    private final InvitacionService service;

    public InvitacionController(InvitacionService service) {
        this.service = service;
    }

    /** Moderador: lista invitaciones de un equipo */
    @GetMapping("/equipos/{equipoId}/invitaciones")
    public ResponseEntity<List<InvitacionResponse>> listar(@PathVariable Long equipoId) {
        return ResponseEntity.ok(
                service.findByEquipo(equipoId).stream().map(InvitacionResponse::from).toList());
    }

    /** Moderador: invitar jugador a un equipo */
    @PostMapping("/equipos/{equipoId}/invitaciones")
    @PreAuthorize("hasAnyRole('ADMIN_PLATAFORMA','MODERADOR')")
    public ResponseEntity<InvitacionResponse> invitar(
            @PathVariable Long equipoId,
            @Valid @RequestBody InvitarRequest request) {
        Long moderadorId = SecurityUtils.getUserId();
        InvitacionEntity inv = service.invitar(
                equipoId, request.email(), request.nombreCompleto(),
                request.areaId(), Boolean.TRUE.equals(request.esCapitan()), moderadorId);
        return ResponseEntity.status(201).body(InvitacionResponse.from(inv));
    }

    /** Moderador: cancelar invitación pendiente */
    @DeleteMapping("/invitaciones/{invitacionId}")
    public ResponseEntity<Void> cancelar(@PathVariable Long invitacionId) {
        Long moderadorId = SecurityUtils.getUserId();
        service.cancelar(invitacionId, moderadorId);
        return ResponseEntity.noContent().build();
    }

    /** Público: consultar invitación por token (para la pantalla de registro) */
    @GetMapping("/invitaciones/token/{token}")
    public ResponseEntity<InvitacionDetalleResponse> porToken(@PathVariable String token) {
        return ResponseEntity.ok(service.findByTokenDetalle(token));
    }

    /** Público: aceptar invitación (el jugador crea o activa su cuenta) */
    @PostMapping("/invitaciones/token/{token}/aceptar")
    public ResponseEntity<Map<String, String>> aceptar(
            @PathVariable String token,
            @Valid @RequestBody AceptarRequest request) {
        service.aceptar(token, request.password());
        return ResponseEntity.ok(Map.of("mensaje", "Invitacion aceptada. Ya puedes iniciar sesion."));
    }

    // --- DTOs ---

    public record InvitarRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 150) String nombreCompleto,
            Long areaId,
            Boolean esCapitan) {}

    public record AceptarRequest(
            @NotBlank @Size(min = 8, max = 128) String password) {}

    public record InvitacionResponse(
            Long id, Long equipoId, String email, String nombreCompleto,
            String estado, String createdAt, String expiresAt) {

        static InvitacionResponse from(InvitacionEntity e) {
            return new InvitacionResponse(
                    e.getId(), e.getEquipoId(), e.getEmail(), e.getNombreCompleto(),
                    e.getEstado(),
                    e.getCreatedAt() != null ? e.getCreatedAt().toString() : null,
                    e.getExpiresAt() != null ? e.getExpiresAt().toString() : null);
        }
    }

    /** Response enriquecido para la pantalla de registro */
    public record InvitacionDetalleResponse(
            Long id, String email, String nombreCompleto, String estado,
            String equipoNombre, String equipoColor,
            String competenciaNombre, String competenciaCodigo,
            String expiresAt) {}
}

package py.simulador.invitacion.importacion;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import py.simulador.config.SecurityUtils;

/**
 * Invitación masiva de miembros a un equipo a partir de un CSV.
 *
 * <p>Endpoint hecho a mano (no generado por OpenAPI), siguiendo el mismo patrón
 * que {@code POST /usuarios/import}: la carga llega como multipart
 * ({@code MultipartFile}, parámetro {@code file}). Reutiliza la lógica de la
 * invitación individual ({@code POST /equipos/{equipoId}/invitaciones}) fila a
 * fila para conseguir éxito parcial.
 *
 * <p>El moderador que ejecuta la carga se toma del contexto de seguridad
 * ({@code SecurityUtils.getUserId()}), igual que en la invitación individual.
 * El gate de autorización (rol MODERADOR/ADMIN_PLATAFORMA) se alinea con el del
 * import de usuarios; la invitación individual hoy solo exige autenticación, pero
 * aquí endurecemos al rol moderador para que un JUGADOR no pueda cargar miembros.
 */
@RestController
public class InvitacionImportController {

    private final InvitacionImportService importService;

    public InvitacionImportController(InvitacionImportService importService) {
        this.importService = importService;
    }

    @PostMapping(
            value = "/equipos/{equipoId}/invitaciones/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN_PLATAFORMA','MODERADOR')")
    public ResponseEntity<InvitacionImportResultDto> importar(
            @PathVariable Long equipoId,
            @RequestParam(name = "file", required = false) MultipartFile file) {
        Long moderadorId = SecurityUtils.getUserId();
        return ResponseEntity.ok(importService.importar(equipoId, moderadorId, file));
    }
}

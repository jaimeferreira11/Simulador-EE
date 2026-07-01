package py.simulador.usuario.importacion;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Importación masiva de usuarios JUGADOR (y otros, según rol) a partir de un CSV.
 *
 * <p>Endpoint hecho a mano (no generado por OpenAPI): el generador del proyecto
 * corre en modo {@code interfaceOnly} y el manejo de multipart con ese setup es
 * incómodo y sin precedentes en el contrato. La carga llega como multipart
 * ({@code MultipartFile}, parámetro {@code file}), que es lo que enviará el
 * uploader del frontend. El gate de autorización es idéntico al de la creación
 * individual ({@code POST /usuarios}).
 */
@RestController
public class UsuarioImportController {

    private final UsuarioImportService importService;

    public UsuarioImportController(UsuarioImportService importService) {
        this.importService = importService;
    }

    @PostMapping(
            value = "/usuarios/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN_PLATAFORMA','MODERADOR')")
    public ResponseEntity<ImportResultDto> importarUsuarios(
            @RequestParam(name = "file", required = false) MultipartFile file) {
        return ResponseEntity.ok(importService.importar(file));
    }
}

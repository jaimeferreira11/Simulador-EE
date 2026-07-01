package py.simulador.admin;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller exclusivo para ADMIN_PLATAFORMA.
 * ABMs: Usuarios, Rubros, ParámetrosMacro, ParámetrosRubro, Eventos, Entidades.
 * Todos los endpoints requieren rol ADMIN_PLATAFORMA.
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN_PLATAFORMA')")
public class AdminController {

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    // =====================================================================
    // USUARIOS
    // =====================================================================

    @GetMapping("/usuarios")
    public ResponseEntity<PagedResponse<AdminDtos.UsuarioRow>> listUsuarios(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) Boolean activo) {
        return ResponseEntity.ok(service.listUsuarios(page, size, q, rol, activo));
    }

    @GetMapping("/usuarios/{id}")
    public ResponseEntity<AdminDtos.UsuarioDetail> getUsuario(@PathVariable Long id) {
        return ResponseEntity.ok(service.getUsuario(id));
    }

    @PostMapping("/usuarios")
    public ResponseEntity<AdminDtos.UsuarioDetail> createUsuario(
            @Valid @RequestBody AdminDtos.UsuarioCreateRequest req) {
        return ResponseEntity.status(201).body(service.createUsuario(req));
    }

    @PutMapping("/usuarios/{id}")
    public ResponseEntity<AdminDtos.UsuarioDetail> updateUsuario(
            @PathVariable Long id, @Valid @RequestBody AdminDtos.UsuarioUpdateRequest req) {
        return ResponseEntity.ok(service.updateUsuario(id, req));
    }

    @PatchMapping("/usuarios/{id}/estado")
    public ResponseEntity<Void> toggleUsuarioActivo(
            @PathVariable Long id, @RequestBody AdminDtos.EstadoRequest req) {
        service.toggleUsuarioActivo(id, req.activo());
        return ResponseEntity.noContent().build();
    }

    // =====================================================================
    // RUBROS
    // =====================================================================

    @GetMapping("/rubros")
    public ResponseEntity<PagedResponse<AdminDtos.RubroRow>> listRubros(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean activo) {
        return ResponseEntity.ok(service.listRubros(page, size, q, activo));
    }

    @GetMapping("/rubros/{id}")
    public ResponseEntity<AdminDtos.RubroDetail> getRubro(@PathVariable Long id) {
        return ResponseEntity.ok(service.getRubro(id));
    }

    @PostMapping("/rubros")
    public ResponseEntity<AdminDtos.RubroDetail> createRubro(
            @Valid @RequestBody AdminDtos.RubroRequest req) {
        return ResponseEntity.status(201).body(service.createRubro(req));
    }

    @PutMapping("/rubros/{id}")
    public ResponseEntity<AdminDtos.RubroDetail> updateRubro(
            @PathVariable Long id, @Valid @RequestBody AdminDtos.RubroRequest req) {
        return ResponseEntity.ok(service.updateRubro(id, req));
    }

    @PatchMapping("/rubros/{id}/estado")
    public ResponseEntity<Void> toggleRubroActivo(
            @PathVariable Long id, @RequestBody AdminDtos.EstadoRequest req) {
        service.toggleRubroActivo(id, req.activo());
        return ResponseEntity.noContent().build();
    }

    // =====================================================================
    // PARAMETROS MACRO
    // =====================================================================

    @GetMapping("/parametros-macro")
    public ResponseEntity<PagedResponse<AdminDtos.ParamMacroRow>> listParamMacro(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean activo) {
        return ResponseEntity.ok(service.listParamMacro(page, size, q, activo));
    }

    @GetMapping("/parametros-macro/{id}")
    public ResponseEntity<AdminDtos.ParamMacroDetail> getParamMacro(@PathVariable Long id) {
        return ResponseEntity.ok(service.getParamMacro(id));
    }

    @PostMapping("/parametros-macro")
    public ResponseEntity<AdminDtos.ParamMacroDetail> createParamMacro(
            @Valid @RequestBody AdminDtos.ParamMacroRequest req) {
        return ResponseEntity.status(201).body(service.createParamMacro(req));
    }

    @PutMapping("/parametros-macro/{id}")
    public ResponseEntity<AdminDtos.ParamMacroDetail> updateParamMacro(
            @PathVariable Long id, @Valid @RequestBody AdminDtos.ParamMacroRequest req) {
        return ResponseEntity.ok(service.updateParamMacro(id, req));
    }

    @PatchMapping("/parametros-macro/{id}/estado")
    public ResponseEntity<Void> toggleParamMacroActivo(
            @PathVariable Long id, @RequestBody AdminDtos.EstadoRequest req) {
        service.toggleParamMacroActivo(id, req.activo());
        return ResponseEntity.noContent().build();
    }

    // Trimestres macro (endpoint separado)
    @GetMapping("/parametros-macro/{id}/trimestres")
    public ResponseEntity<List<AdminDtos.MacroTrimestreDto>> getParamMacroTrimestres(
            @PathVariable Long id) {
        return ResponseEntity.ok(service.getParamMacroTrimestres(id));
    }

    @PutMapping("/parametros-macro/{id}/trimestres")
    public ResponseEntity<List<AdminDtos.MacroTrimestreDto>> replaceParamMacroTrimestres(
            @PathVariable Long id,
            @Valid @RequestBody List<AdminDtos.MacroTrimestreDto> trimestres) {
        return ResponseEntity.ok(service.replaceParamMacroTrimestres(id, trimestres));
    }

    // =====================================================================
    // PARAMETROS RUBRO
    // =====================================================================

    @GetMapping("/parametros-rubro")
    public ResponseEntity<PagedResponse<AdminDtos.ParamRubroRow>> listParamRubro(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long rubroId,
            @RequestParam(required = false) Boolean activo) {
        return ResponseEntity.ok(service.listParamRubro(page, size, q, rubroId, activo));
    }

    @GetMapping("/parametros-rubro/{id}")
    public ResponseEntity<AdminDtos.ParamRubroDetail> getParamRubro(@PathVariable Long id) {
        return ResponseEntity.ok(service.getParamRubro(id));
    }

    @PostMapping("/parametros-rubro")
    public ResponseEntity<AdminDtos.ParamRubroDetail> createParamRubro(
            @Valid @RequestBody AdminDtos.ParamRubroRequest req) {
        return ResponseEntity.status(201).body(service.createParamRubro(req));
    }

    @PutMapping("/parametros-rubro/{id}")
    public ResponseEntity<AdminDtos.ParamRubroDetail> updateParamRubro(
            @PathVariable Long id, @Valid @RequestBody AdminDtos.ParamRubroRequest req) {
        return ResponseEntity.ok(service.updateParamRubro(id, req));
    }

    @PatchMapping("/parametros-rubro/{id}/estado")
    public ResponseEntity<Void> toggleParamRubroActivo(
            @PathVariable Long id, @RequestBody AdminDtos.EstadoRequest req) {
        service.toggleParamRubroActivo(id, req.activo());
        return ResponseEntity.noContent().build();
    }

    // Trimestres rubro (endpoint separado)
    @GetMapping("/parametros-rubro/{id}/trimestres")
    public ResponseEntity<List<AdminDtos.RubroTrimestreDto>> getParamRubroTrimestres(
            @PathVariable Long id) {
        return ResponseEntity.ok(service.getParamRubroTrimestres(id));
    }

    @PutMapping("/parametros-rubro/{id}/trimestres")
    public ResponseEntity<List<AdminDtos.RubroTrimestreDto>> replaceParamRubroTrimestres(
            @PathVariable Long id,
            @Valid @RequestBody List<AdminDtos.RubroTrimestreDto> trimestres) {
        return ResponseEntity.ok(service.replaceParamRubroTrimestres(id, trimestres));
    }

    // =====================================================================
    // EVENTOS CATALOGO
    // =====================================================================

    @GetMapping("/eventos")
    public ResponseEntity<PagedResponse<AdminDtos.EventoRow>> listEventos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long rubroId,
            @RequestParam(required = false) String severidad,
            @RequestParam(required = false) Boolean activo) {
        return ResponseEntity.ok(service.listEventos(page, size, q, rubroId, severidad, activo));
    }

    @GetMapping("/eventos/{id}")
    public ResponseEntity<AdminDtos.EventoDetail> getEvento(@PathVariable Long id) {
        return ResponseEntity.ok(service.getEvento(id));
    }

    @PostMapping("/eventos")
    public ResponseEntity<AdminDtos.EventoDetail> createEvento(
            @Valid @RequestBody AdminDtos.EventoRequest req) {
        return ResponseEntity.status(201).body(service.createEvento(req));
    }

    @PutMapping("/eventos/{id}")
    public ResponseEntity<AdminDtos.EventoDetail> updateEvento(
            @PathVariable Long id, @Valid @RequestBody AdminDtos.EventoRequest req) {
        return ResponseEntity.ok(service.updateEvento(id, req));
    }

    @PatchMapping("/eventos/{id}/estado")
    public ResponseEntity<Void> toggleEventoActivo(
            @PathVariable Long id, @RequestBody AdminDtos.EstadoRequest req) {
        service.toggleEventoActivo(id, req.activo());
        return ResponseEntity.noContent().build();
    }

    // =====================================================================
    // ENTIDADES
    // =====================================================================

    @GetMapping("/entidades")
    public ResponseEntity<PagedResponse<AdminDtos.EntidadRow>> listEntidades(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean activa) {
        return ResponseEntity.ok(service.listEntidades(page, size, q, activa));
    }

    @GetMapping("/entidades/{id}")
    public ResponseEntity<AdminDtos.EntidadDetail> getEntidad(@PathVariable Long id) {
        return ResponseEntity.ok(service.getEntidad(id));
    }

    @PostMapping("/entidades")
    public ResponseEntity<AdminDtos.EntidadDetail> createEntidad(
            @Valid @RequestBody AdminDtos.EntidadRequest req) {
        return ResponseEntity.status(201).body(service.createEntidad(req));
    }

    @PutMapping("/entidades/{id}")
    public ResponseEntity<AdminDtos.EntidadDetail> updateEntidad(
            @PathVariable Long id, @Valid @RequestBody AdminDtos.EntidadRequest req) {
        return ResponseEntity.ok(service.updateEntidad(id, req));
    }

    @PatchMapping("/entidades/{id}/estado")
    public ResponseEntity<Void> toggleEntidadActiva(
            @PathVariable Long id, @RequestBody AdminDtos.EstadoRequest req) {
        service.toggleEntidadActiva(id, req.activo());
        return ResponseEntity.noContent().build();
    }
}

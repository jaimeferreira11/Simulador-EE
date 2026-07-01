package py.simulador.usuario;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import py.simulador.api.generated.UsuariosApi;
import py.simulador.api.generated.model.PagedUsuarios;
import py.simulador.api.generated.model.Usuario;
import py.simulador.api.generated.model.UsuarioCreate;
import py.simulador.api.generated.model.UsuarioUpdate;
import py.simulador.auth.PasswordResetService;
import py.simulador.auth.RolCache;
import py.simulador.common.AccessDeniedException;
import py.simulador.config.SecurityUtils;

import java.util.List;

@RestController
public class UsuarioController implements UsuariosApi {

    private final UsuarioService service;
    private final UsuarioMapper mapper;
    private final RolCache rolCache;
    private final PasswordResetService passwordResetService;

    public UsuarioController(UsuarioService service, UsuarioMapper mapper, RolCache rolCache,
                             PasswordResetService passwordResetService) {
        this.service = service;
        this.mapper = mapper;
        this.rolCache = rolCache;
        this.passwordResetService = passwordResetService;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN_PLATAFORMA','MODERADOR')")
    public ResponseEntity<PagedUsuarios> usuariosGet(Integer page, Integer size,
                                                      String rol, Boolean activo, String search) {
        List<UsuarioEntity> entities = service.findAll(rol, activo, search);
        List<Usuario> dtos = entities.stream().map(e -> mapper.toDto(e, rolCache)).toList();
        PagedUsuarios paged = new PagedUsuarios();
        paged.setContent(dtos);
        paged.setTotalElements((long) dtos.size());
        paged.setPage(page != null ? page : 0);
        paged.setSize(size != null ? size : 20);
        return ResponseEntity.ok(paged);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN_PLATAFORMA','MODERADOR')")
    public ResponseEntity<Usuario> usuariosPost(UsuarioCreate usuarioCreate) {
        UsuarioEntity entity = service.create(usuarioCreate);
        // La cuenta se crea con una contrasena que el usuario no conoce; se le envia
        // un email (flujo de reset) para que defina la suya y pueda iniciar sesion.
        // Best-effort: un fallo de envio no aborta la creacion (201 igual).
        passwordResetService.enviarSetPasswordInicial(entity);
        return ResponseEntity.status(201).body(mapper.toDto(entity, rolCache));
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN_PLATAFORMA','MODERADOR')")
    public ResponseEntity<Usuario> usuariosIdGet(Long id) {
        return ResponseEntity.ok(mapper.toDto(service.findById(id), rolCache));
    }

    @Override
    public ResponseEntity<Usuario> usuariosIdPatch(Long id, UsuarioUpdate usuarioUpdate) {
        // Autorizacion "propio o ADMIN": el usuario solo puede editar su propio perfil,
        // salvo ADMIN_PLATAFORMA que puede editar cualquiera. Cualquier otro caso -> 403.
        // (UsuarioService.update solo toca nombre/password; rol_codigo se ignora, sin elevacion.)
        if (!SecurityUtils.getUserId().equals(id)
                && !"ADMIN_PLATAFORMA".equals(SecurityUtils.getRol())) {
            throw new AccessDeniedException(
                    "Solo puedes actualizar tu propio perfil");
        }
        return ResponseEntity.ok(mapper.toDto(service.update(id, usuarioUpdate), rolCache));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN_PLATAFORMA')")
    public ResponseEntity<Void> usuariosIdDelete(Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

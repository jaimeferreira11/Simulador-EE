package py.simulador.usuario;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.auth.RolCache;
import py.simulador.common.AccessDeniedException;
import py.simulador.common.BusinessValidationException;
import py.simulador.common.ResourceNotFoundException;
import py.simulador.config.SecurityUtils;
import py.simulador.api.generated.model.UsuarioCreate;
import py.simulador.api.generated.model.UsuarioUpdate;

import java.util.List;
import java.util.Set;

@Service
public class UsuarioService {

    /**
     * Roles internos del sistema que nunca deben aparecer en la gestion de usuarios.
     * El usuario 'system-bot@simulador.local' (rol SYSTEM) audita decisiones de bots:
     * no es una cuenta administrable y su rol no existe en el enum del DTO publico, por
     * lo que dejarlo llegar al mapper provocaria un 500. Se excluye de la lista.
     */
    private static final Set<String> ROLES_INTERNOS = Set.of("SYSTEM");

    private final UsuarioRepository repo;
    private final RolCache rolCache;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UsuarioService(UsuarioRepository repo, RolCache rolCache) {
        this.repo = repo;
        this.rolCache = rolCache;
    }

    @Transactional(readOnly = true)
    public List<UsuarioEntity> findAll(String rol, Boolean activo, String search) {
        List<UsuarioEntity> all = (List<UsuarioEntity>) repo.findAll();

        return all.stream()
                .filter(u -> {
                    String rolCodigo = rolCache.getCodigo(u.getRolUsuarioId());
                    // Las cuentas internas (p.ej. SYSTEM) no son administrables y no se exponen.
                    if (rolCodigo != null && ROLES_INTERNOS.contains(rolCodigo)) return false;
                    if (rol != null) {
                        if (!rol.equalsIgnoreCase(rolCodigo)) return false;
                    }
                    if (activo != null && u.isActivo() != activo) return false;
                    if (search != null && !search.isBlank()) {
                        String term = search.toLowerCase();
                        boolean matchEmail = u.getEmail().toLowerCase().contains(term);
                        boolean matchNombre = u.getNombreCompleto().toLowerCase().contains(term);
                        if (!matchEmail && !matchNombre) return false;
                    }
                    return true;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioEntity findById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
    }

    @Transactional
    public UsuarioEntity create(UsuarioCreate input) {
        String rolCodigo = input.getRolCodigo().getValue();
        enforceRolCreationPolicy(rolCodigo);

        repo.findByEmail(input.getEmail()).ifPresent(existing -> {
            throw new BusinessValidationException("El email ya esta registrado");
        });

        UsuarioEntity entity = new UsuarioEntity();
        entity.setEmail(input.getEmail());
        entity.setPasswordHash(encoder.encode(input.getPassword()));
        entity.setNombreCompleto(input.getNombreCompleto());
        entity.setRolUsuarioId(resolveRolId(rolCodigo));
        entity.setActivo(true);
        entity.setEmailVerificado(false);
        return repo.save(entity);
    }

    @Transactional
    public UsuarioEntity update(Long id, UsuarioUpdate input) {
        UsuarioEntity entity = findById(id);
        if (input.getNombreCompleto() != null) {
            entity.setNombreCompleto(input.getNombreCompleto());
        }
        if (input.getPassword() != null) {
            entity.setPasswordHash(encoder.encode(input.getPassword()));
        }
        return repo.save(entity);
    }

    @Transactional
    public void delete(Long id) {
        UsuarioEntity entity = findById(id);
        entity.setActivo(false);
        repo.save(entity);
    }

    /**
     * Separación de roles al crear usuarios:
     * - ADMIN_PLATAFORMA puede crear cualquier rol.
     * - MODERADOR solo puede crear usuarios con rol JUGADOR.
     * Cualquier otro intento del MODERADOR se rechaza con 403.
     */
    private void enforceRolCreationPolicy(String rolCodigoSolicitado) {
        String rolActor = SecurityUtils.getRol();
        if ("MODERADOR".equals(rolActor) && !"JUGADOR".equals(rolCodigoSolicitado)) {
            throw new AccessDeniedException(
                    "Un MODERADOR solo puede crear usuarios con rol JUGADOR");
        }
    }

    private Long resolveRolId(String rolCodigo) {
        Long rolId = rolCache.getId(rolCodigo);
        if (rolId == null) {
            throw new BusinessValidationException("Rol desconocido: " + rolCodigo);
        }
        return rolId;
    }
}

package py.simulador.invitacion;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.auth.RolCache;
import py.simulador.common.BusinessValidationException;
import py.simulador.common.ResourceNotFoundException;
import py.simulador.common.AccessDeniedException;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.config.SecurityUtils;
import py.simulador.email.EmailService;
import py.simulador.equipo.EquipoEntity;
import py.simulador.equipo.EquipoMiembroEntity;
import py.simulador.equipo.EquipoMiembroRepository;
import py.simulador.equipo.EquipoRepository;
import py.simulador.notificacion.NotificacionService;
import py.simulador.usuario.UsuarioEntity;
import py.simulador.usuario.UsuarioRepository;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class InvitacionService {

    private static final int TOKEN_BYTES = 32;
    private static final int EXPIRACION_DIAS = 7;

    private final InvitacionRepository invitacionRepo;
    private final EquipoRepository equipoRepo;
    private final CompetenciaRepository competenciaRepo;
    private final EquipoMiembroRepository miembroRepo;
    private final UsuarioRepository usuarioRepo;
    private final PasswordEncoder passwordEncoder;
    private final RolCache rolCache;
    private final EmailService emailService;
    private final NotificacionService notificacionService;
    private final SecureRandom secureRandom = new SecureRandom();

    public InvitacionService(InvitacionRepository invitacionRepo,
                             EquipoRepository equipoRepo,
                             CompetenciaRepository competenciaRepo,
                             EquipoMiembroRepository miembroRepo,
                             UsuarioRepository usuarioRepo,
                             PasswordEncoder passwordEncoder,
                             RolCache rolCache,
                             EmailService emailService,
                             NotificacionService notificacionService) {
        this.invitacionRepo = invitacionRepo;
        this.equipoRepo = equipoRepo;
        this.competenciaRepo = competenciaRepo;
        this.miembroRepo = miembroRepo;
        this.usuarioRepo = usuarioRepo;
        this.passwordEncoder = passwordEncoder;
        this.rolCache = rolCache;
        this.emailService = emailService;
        this.notificacionService = notificacionService;
    }

    @Transactional(readOnly = true)
    public List<InvitacionEntity> findByEquipo(Long equipoId) {
        return invitacionRepo.findByEquipoId(equipoId);
    }

    /**
     * Verifica una sola vez que el usuario actual pueda invitar a este equipo
     * (su competencia le pertenece, o es ADMIN_PLATAFORMA). Pensado para que el
     * import en lote falle con 403 de forma global —sin que el chequeo quede
     * atrapado como un error por fila— antes de iterar el CSV.
     *
     * @throws AccessDeniedException si un MODERADOR no es dueño de la competencia
     */
    @Transactional(readOnly = true)
    public void verificarAccesoEquipo(Long equipoId, Long usuarioId) {
        EquipoEntity equipo = equipoRepo.findById(equipoId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipo", equipoId));
        CompetenciaEntity competencia = competenciaRepo.findById(equipo.getCompetenciaId())
                .orElseThrow(() -> new ResourceNotFoundException("Competencia", equipo.getCompetenciaId()));
        verificarPropietario(competencia, usuarioId);
    }

    /**
     * Moderador invita a un jugador a un equipo.
     * Si ya existe una invitación pendiente para el mismo email+equipo, la retorna.
     * Valida que el usuario no esté ya en otro equipo de la misma competencia.
     */
    @Transactional
    public InvitacionEntity invitar(Long equipoId, String email, String nombreCompleto,
                                     Long areaId, boolean esCapitan, Long moderadorId) {
        EquipoEntity equipo = equipoRepo.findById(equipoId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipo", equipoId));

        CompetenciaEntity competencia = competenciaRepo.findById(equipo.getCompetenciaId())
                .orElseThrow(() -> new ResourceNotFoundException("Competencia", equipo.getCompetenciaId()));

        // Authorization: a MODERADOR may only invite to teams of competitions they own.
        // ADMIN_PLATAFORMA bypasses the ownership check. Same gate applies to single
        // and bulk-import paths, since both route through this method.
        verificarPropietario(competencia, moderadorId);

        // Check if already invited (pending) — dedup: re-inviting a pending email is a
        // no-op that returns the existing invitation, so it never trips the capacity limit.
        Optional<InvitacionEntity> existente = invitacionRepo
                .findPendienteByEquipoIdAndEmail(equipoId, email);
        if (existente.isPresent()) {
            return existente.get();
        }

        // Team capacity (optional, configured per competition). NULL = unlimited.
        // Capacity = current members + pending invitations; accepting this new invite
        // must not exceed the configured maximum.
        Short maxIntegrantes = competencia.getMaxIntegrantesEquipo();
        if (maxIntegrantes != null) {
            long miembrosActuales = miembroRepo.findByEquipoId(equipoId).size();
            long invitacionesPendientes = invitacionRepo.countPendientesByEquipoId(equipoId);
            if (miembrosActuales + invitacionesPendientes + 1 > maxIntegrantes) {
                throw new BusinessValidationException(
                        "El equipo alcanzó el máximo de " + maxIntegrantes
                                + " integrantes (miembros + invitaciones pendientes)");
            }
        }

        // Check if user already exists and is in another team of same competition
        usuarioRepo.findByEmail(email).ifPresent(usuario -> {
            List<EquipoEntity> equiposComp = equipoRepo.findByCompetenciaId(equipo.getCompetenciaId());
            for (EquipoEntity otroEquipo : equiposComp) {
                miembroRepo.findByEquipoIdAndUsuarioId(otroEquipo.getId(), usuario.getId())
                        .ifPresent(m -> {
                            throw new BusinessValidationException(
                                    "El jugador ya pertenece al equipo '" + otroEquipo.getNombreEmpresa()
                                            + "' en esta competencia");
                        });
            }
        });

        InvitacionEntity inv = new InvitacionEntity();
        inv.setEquipoId(equipoId);
        inv.setEmail(email.toLowerCase().trim());
        inv.setNombreCompleto(nombreCompleto);
        inv.setToken(generateToken());
        inv.setAreaId(areaId);
        inv.setEsCapitan(esCapitan);
        inv.setEstado("PENDIENTE");
        inv.setCreadaPor(moderadorId);
        inv.setExpiresAt(OffsetDateTime.now().plusDays(EXPIRACION_DIAS));
        InvitacionEntity saved = invitacionRepo.save(inv);

        // Send invitation email (async — won't block the transaction)
        String compNombre = competencia.getNombre() != null ? competencia.getNombre() : "Competencia";
        emailService.enviarInvitacion(
                email, nombreCompleto,
                equipo.getNombreEmpresa(), compNombre,
                equipo.getCodigoColor(), saved.getToken());

        // Notify existing user (new users get notified after accepting)
        usuarioRepo.findByEmail(email).ifPresent(usuario ->
            notificacionService.notificarUsuario(usuario.getId(), equipo.getCompetenciaId(),
                    "INVITACION_RECIBIDA",
                    "Fuiste invitado al equipo " + equipo.getNombreEmpresa() + " en " + compNombre,
                    "El moderador te invitó a participar en la competencia " + compNombre + " como miembro del equipo " + equipo.getNombreEmpresa() + ".",
                    "IMPORTANTE")
        );

        return saved;
    }

    /**
     * Jugador acepta la invitación: si no tiene cuenta se crea con la contraseña dada,
     * si ya tiene cuenta solo se agrega al equipo.
     */
    @Transactional
    public UsuarioEntity aceptar(String token, String password) {
        InvitacionEntity inv = invitacionRepo.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitacion", "token", token));

        if (!"PENDIENTE".equals(inv.getEstado())) {
            throw new BusinessValidationException("La invitacion ya fue " + inv.getEstado().toLowerCase());
        }

        if (inv.getExpiresAt().isBefore(OffsetDateTime.now())) {
            inv.setEstado("EXPIRADA");
            invitacionRepo.save(inv);
            throw new BusinessValidationException("La invitacion ha expirado");
        }

        // Find or create user
        Optional<UsuarioEntity> existente = usuarioRepo.findByEmail(inv.getEmail());
        UsuarioEntity usuario;
        if (existente.isPresent()) {
            usuario = existente.get();
            if (!usuario.isActivo()) {
                throw new BusinessValidationException("Tu cuenta esta desactivada, contacta al administrador");
            }
        } else {
            // Create new user with JUGADOR role
            usuario = new UsuarioEntity();
            usuario.setEmail(inv.getEmail());
            usuario.setNombreCompleto(inv.getNombreCompleto());
            usuario.setPasswordHash(passwordEncoder.encode(password));
            Long rolJugador = rolCache.getId("JUGADOR");
            if (rolJugador == null) {
                throw new IllegalStateException("Rol JUGADOR no encontrado en catálogo");
            }
            usuario.setRolUsuarioId(rolJugador);
            usuario.setActivo(true);
            usuario.setEmailVerificado(true);
            usuario = usuarioRepo.save(usuario);
        }

        // Add to team (if not already member)
        Optional<EquipoMiembroEntity> yaMiembro = miembroRepo
                .findByEquipoIdAndUsuarioId(inv.getEquipoId(), usuario.getId());
        if (yaMiembro.isEmpty()) {
            // If setting as captain, unset current captain
            if (inv.isEsCapitan()) {
                List<EquipoMiembroEntity> miembros = miembroRepo.findByEquipoId(inv.getEquipoId());
                for (EquipoMiembroEntity m : miembros) {
                    if (m.isEsCapitan()) {
                        m.setEsCapitan(false);
                        miembroRepo.save(m);
                    }
                }
            }
            EquipoMiembroEntity miembro = new EquipoMiembroEntity();
            miembro.setEquipoId(inv.getEquipoId());
            miembro.setUsuarioId(usuario.getId());
            miembro.setAreaId(inv.getAreaId());
            miembro.setEsCapitan(inv.isEsCapitan());
            miembro.setJoinedAt(OffsetDateTime.now());
            miembroRepo.save(miembro);
        }

        // Mark invitation as accepted
        inv.setEstado("ACEPTADA");
        inv.setAcceptedAt(OffsetDateTime.now());
        invitacionRepo.save(inv);

        return usuario;
    }

    /**
     * Consultar invitación por token (para el frontend: mostrar nombre, equipo, etc.)
     */
    @Transactional(readOnly = true)
    public InvitacionEntity findByToken(String token) {
        InvitacionEntity inv = invitacionRepo.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitacion", "token", token));
        if (inv.getExpiresAt().isBefore(OffsetDateTime.now()) && "PENDIENTE".equals(inv.getEstado())) {
            inv.setEstado("EXPIRADA");
        }
        return inv;
    }

    /**
     * Consultar invitación con datos enriquecidos de equipo y competencia.
     */
    @Transactional(readOnly = true)
    public InvitacionController.InvitacionDetalleResponse findByTokenDetalle(String token) {
        InvitacionEntity inv = findByToken(token);
        EquipoEntity equipo = equipoRepo.findById(inv.getEquipoId()).orElse(null);
        String equipoNombre = equipo != null ? equipo.getNombreEmpresa() : "";
        String equipoColor = equipo != null ? equipo.getCodigoColor() : "#006B3F";

        String compNombre = "";
        String compCodigo = "";
        if (equipo != null) {
            var comp = competenciaRepo.findById(equipo.getCompetenciaId()).orElse(null);
            if (comp != null) {
                compNombre = comp.getNombre();
                compCodigo = comp.getCodigo();
            }
        }

        return new InvitacionController.InvitacionDetalleResponse(
                inv.getId(), inv.getEmail(), inv.getNombreCompleto(), inv.getEstado(),
                equipoNombre, equipoColor, compNombre, compCodigo,
                inv.getExpiresAt() != null ? inv.getExpiresAt().toString() : null);
    }

    @Transactional
    public void cancelar(Long invitacionId, Long moderadorId) {
        InvitacionEntity inv = invitacionRepo.findById(invitacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitacion", invitacionId));
        if (!"PENDIENTE".equals(inv.getEstado())) {
            throw new BusinessValidationException("Solo se pueden cancelar invitaciones pendientes");
        }
        invitacionRepo.delete(inv);
    }

    /**
     * Verifica que el usuario actual pueda invitar a equipos de esta competencia.
     * Un MODERADOR solo puede operar sobre competencias que le pertenecen
     * ({@code competencia.moderador_id}); un ADMIN_PLATAFORMA omite esta verificación.
     * Si no hay rol en el contexto (p.ej. invocación interna sin seguridad), no se aplica.
     */
    private void verificarPropietario(CompetenciaEntity competencia, Long usuarioId) {
        String rol = SecurityUtils.getRolOrNull();
        if (rol == null || "ADMIN_PLATAFORMA".equals(rol)) {
            return; // admin (o contexto interno) omite la verificación de propiedad
        }
        if (!usuarioId.equals(competencia.getModeradorId())) {
            throw new AccessDeniedException(
                    "No tienes permiso para invitar a equipos de esta competencia");
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

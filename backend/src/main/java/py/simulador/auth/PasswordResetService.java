package py.simulador.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.email.EmailService;
import py.simulador.usuario.UsuarioEntity;
import py.simulador.usuario.UsuarioRepository;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_BYTES = 32; // 32 bytes = 64 hex chars
    private static final int EXPIRY_HOURS = 1;
    // Activacion de cuenta recien provisionada: ventana mas amplia que un reset
    // normal, porque el usuario no solicito el email y puede no revisarlo de inmediato
    // (sobre todo en carga masiva).
    private static final int ACTIVATION_EXPIRY_HOURS = 72;

    private final UsuarioRepository usuarioRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UsuarioRepository usuarioRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /**
     * Solicita un reset de contrasena. Si el email no existe, no hace nada
     * (no se revela si el usuario existe o no).
     * Retorna el token generado (para uso futuro con envio de email).
     */
    @Transactional
    public Optional<String> requestReset(String email) {
        Optional<UsuarioEntity> optUser = usuarioRepository.findByEmail(email);
        if (optUser.isEmpty()) {
            log.debug("Password reset solicitado para email inexistente: {}", email);
            return Optional.empty();
        }

        UsuarioEntity usuario = optUser.get();

        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String token = HexFormat.of().formatHex(randomBytes);

        PasswordResetToken resetToken = new PasswordResetToken(
                usuario.getId(),
                token,
                OffsetDateTime.now().plusHours(EXPIRY_HOURS)
        );
        tokenRepository.save(resetToken);

        log.info("Token de reset generado para usuario id={}", usuario.getId());
        return Optional.of(token);
    }

    /**
     * Aprovisionamiento de cuenta: genera un token de reset para un usuario recien
     * creado por un MODERADOR/ADMIN (que no conoce su contrasena) y dispara el email
     * para que la defina. Reutiliza por completo la infraestructura de reset.
     *
     * <p>Best-effort: si la persistencia del token o el disparo del email fallan, se
     * registra y se traga el error — la creacion del usuario NO debe fallar por esto
     * (especialmente en el import masivo, donde la fila ya quedo "creada").
     */
    @Transactional
    public void enviarSetPasswordInicial(UsuarioEntity usuario) {
        try {
            byte[] randomBytes = new byte[TOKEN_BYTES];
            secureRandom.nextBytes(randomBytes);
            String token = HexFormat.of().formatHex(randomBytes);

            PasswordResetToken resetToken = new PasswordResetToken(
                    usuario.getId(),
                    token,
                    OffsetDateTime.now().plusHours(ACTIVATION_EXPIRY_HOURS)
            );
            tokenRepository.save(resetToken);

            emailService.enviarSetPassword(usuario.getEmail(), usuario.getNombreCompleto(), token);
            log.info("Token de set-password generado para usuario id={}", usuario.getId());
        } catch (RuntimeException e) {
            log.error("No se pudo aprovisionar el set-password para usuario id={}: {}",
                    usuario.getId(), e.getMessage());
        }
    }

    /**
     * Resetea la contrasena usando el token. Valida que el token exista,
     * no este usado y no este expirado.
     *
     * @throws AuthException si el token es invalido o expirado
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByTokenAndNotUsed(token)
                .orElseThrow(() -> new AuthException("Token de reset invalido o ya utilizado"));

        if (resetToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new AuthException("Token de reset expirado");
        }

        UsuarioEntity usuario = usuarioRepository.findById(resetToken.getUsuarioId())
                .orElseThrow(() -> new AuthException("Usuario no encontrado"));

        usuario.setPasswordHash(passwordEncoder.encode(newPassword));
        usuarioRepository.save(usuario);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        log.info("Contrasena reseteada exitosamente para usuario id={}", usuario.getId());
    }
}

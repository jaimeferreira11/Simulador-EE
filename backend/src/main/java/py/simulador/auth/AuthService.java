package py.simulador.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.config.JwtProperties;
import py.simulador.config.JwtUtil;
import py.simulador.usuario.UsuarioEntity;
import py.simulador.usuario.UsuarioRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final RolCache rolCache;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UsuarioRepository usuarioRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       JwtProperties jwtProperties,
                       RolCache rolCache) {
        this.usuarioRepository = usuarioRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.jwtProperties = jwtProperties;
        this.rolCache = rolCache;
    }

    @Transactional
    public TokenPair login(String email, String password) {
        // Trim espacios de email y password — defensa contra autocomplete del browser,
        // copy-paste con espacios accidentales, mobile keyboards que insertan espacio
        // al final de palabras autocompletadas, etc.
        String emailLimpio = email == null ? "" : email.trim();
        String passwordLimpio = password == null ? "" : password.trim();

        UsuarioEntity usuario = usuarioRepository.findByEmail(emailLimpio)
                .orElseThrow(() -> new AuthException("Email o contrasena incorrectos"));

        if (!usuario.isActivo()) {
            throw new AuthException("Su cuenta esta desactivada, contacte al administrador");
        }

        if (!passwordEncoder.matches(passwordLimpio, usuario.getPasswordHash())) {
            throw new AuthException("Email o contrasena incorrectos");
        }

        usuario.setLastLoginAt(OffsetDateTime.now());
        usuarioRepository.save(usuario);

        return generateTokenPair(usuario);
    }

    @Transactional
    public TokenPair refresh(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndNotRevoked(hash)
                .orElseThrow(() -> new AuthException("Refresh token invalido o expirado"));

        if (stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            stored.revocar();
            refreshTokenRepository.save(stored);
            throw new AuthException("Refresh token expirado");
        }

        // Revocar el token usado (rotacion)
        stored.revocar();
        refreshTokenRepository.save(stored);

        UsuarioEntity usuario = usuarioRepository.findById(stored.getUsuarioId())
                .orElseThrow(() -> new AuthException("Usuario no encontrado"));

        if (!usuario.isActivo()) {
            throw new AuthException("Su cuenta esta desactivada, contacte al administrador");
        }

        return generateTokenPair(usuario);
    }

    @Transactional
    public void logout(Long usuarioId) {
        refreshTokenRepository.revokeAllByUsuarioId(usuarioId);
    }

    private TokenPair generateTokenPair(UsuarioEntity usuario) {
        String rol = rolCache.getCodigo(usuario.getRolUsuarioId());
        String accessToken = jwtUtil.generateAccessToken(usuario.getId(), usuario.getEmail(), rol);

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        RefreshToken refreshToken = new RefreshToken(
                usuario.getId(),
                hashToken(rawRefreshToken),
                OffsetDateTime.now().plus(jwtProperties.refreshExpiration())
        );
        refreshTokenRepository.save(refreshToken);

        return new TokenPair(accessToken, rawRefreshToken);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public record TokenPair(String accessToken, String refreshToken) {}
}

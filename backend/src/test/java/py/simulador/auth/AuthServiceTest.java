package py.simulador.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import py.simulador.config.JwtProperties;
import py.simulador.config.JwtUtil;
import py.simulador.usuario.UsuarioEntity;
import py.simulador.usuario.UsuarioRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UsuarioRepository usuarioRepo;
    @Mock RefreshTokenRepository refreshTokenRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock RolCache rolCache;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(
                "test-secret-key-must-be-at-least-256-bits-long!!",
                Duration.ofMinutes(15),
                Duration.ofDays(30));
        authService = new AuthService(usuarioRepo, refreshTokenRepo, passwordEncoder,
                jwtUtil, props, rolCache);
    }

    @Test
    void loginExitoso() {
        UsuarioEntity user = crearUsuario();
        when(usuarioRepo.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(rolCache.getCodigo(1L)).thenReturn("JUGADOR");
        when(jwtUtil.generateAccessToken(eq(1L), eq("test@example.com"), eq("JUGADOR")))
                .thenReturn("access-token");
        when(usuarioRepo.save(any())).thenReturn(user);
        when(refreshTokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthService.TokenPair tokens = authService.login("test@example.com", "password123");

        assertNotNull(tokens.accessToken());
        assertNotNull(tokens.refreshToken());
        assertEquals("access-token", tokens.accessToken());
        verify(usuarioRepo).save(argThat(u -> u.getLastLoginAt() != null));
    }

    @Test
    void loginPasswordIncorrecto() {
        UsuarioEntity user = crearUsuario();
        when(usuarioRepo.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(AuthException.class,
                () -> authService.login("test@example.com", "wrong"));
    }

    @Test
    void loginEmailNoExiste() {
        when(usuarioRepo.findByEmail("no@existe.com")).thenReturn(Optional.empty());

        assertThrows(AuthException.class,
                () -> authService.login("no@existe.com", "password"));
    }

    @Test
    void loginUsuarioInactivo() {
        UsuarioEntity user = crearUsuario();
        user.setActivo(false);
        when(usuarioRepo.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        assertThrows(AuthException.class,
                () -> authService.login("test@example.com", "password123"));
    }

    @Test
    void refreshTokenExpirado() {
        RefreshToken stored = new RefreshToken(1L, "hash", OffsetDateTime.now().minusDays(1));
        when(refreshTokenRepo.findByTokenHashAndNotRevoked(anyString()))
                .thenReturn(Optional.of(stored));
        when(refreshTokenRepo.save(any())).thenReturn(stored);

        assertThrows(AuthException.class,
                () -> authService.refresh("some-token"));
    }

    @Test
    void logoutRevocaTodos() {
        authService.logout(1L);
        verify(refreshTokenRepo).revokeAllByUsuarioId(1L);
    }

    private UsuarioEntity crearUsuario() {
        UsuarioEntity user = new UsuarioEntity();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setPasswordHash("hashed");
        user.setNombreCompleto("Test User");
        user.setRolUsuarioId(1L);
        user.setActivo(true);
        return user;
    }
}

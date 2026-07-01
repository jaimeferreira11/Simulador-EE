package py.simulador.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import py.simulador.email.EmailService;
import py.simulador.usuario.UsuarioEntity;
import py.simulador.usuario.UsuarioRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Cubre el aprovisionamiento de set-password de {@link PasswordResetService}:
 * - Se persiste un token y se dispara el email para el usuario recien creado.
 * - Resiliencia: un fallo al enviar el email NO propaga la excepcion (la creacion
 *   del usuario nunca debe fallar por el correo, sobre todo en el import masivo).
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock UsuarioRepository usuarioRepository;
    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(usuarioRepository, tokenRepository,
                passwordEncoder, emailService);
    }

    private UsuarioEntity nuevoUsuario() {
        UsuarioEntity u = new UsuarioEntity();
        u.setId(42L);
        u.setEmail("nuevo@test.com");
        u.setNombreCompleto("Nuevo Usuario");
        return u;
    }

    @Test
    void enviarSetPasswordInicialPersisteTokenYDisparaEmail() {
        UsuarioEntity u = nuevoUsuario();

        service.enviarSetPasswordInicial(u);

        verify(tokenRepository).save(argThat(t ->
                t.getUsuarioId() == 42L && t.getToken() != null && !t.isUsed()));
        verify(emailService).enviarSetPassword(eq("nuevo@test.com"), eq("Nuevo Usuario"), anyString());
    }

    @Test
    void enviarSetPasswordInicialNoPropagaFallosDeEmail() {
        UsuarioEntity u = nuevoUsuario();
        doThrow(new RuntimeException("smtp caido"))
                .when(emailService).enviarSetPassword(anyString(), anyString(), anyString());

        // No debe lanzar: la creacion del usuario no puede abortarse por el email.
        assertDoesNotThrow(() -> service.enviarSetPasswordInicial(u));
    }
}

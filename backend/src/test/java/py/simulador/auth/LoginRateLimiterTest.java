package py.simulador.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTest {

    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new LoginRateLimiter();
    }

    @Test
    void noBloqueoInicialmente() {
        assertFalse(limiter.isBlocked("192.168.1.1"));
    }

    @Test
    void bloqueoTras10Intentos() {
        String ip = "192.168.1.1";
        for (int i = 0; i < 10; i++) {
            assertFalse(limiter.isBlocked(ip), "No debería estar bloqueado tras " + i + " intentos");
            limiter.recordFailedAttempt(ip);
        }
        assertTrue(limiter.isBlocked(ip), "Debería estar bloqueado tras 10 intentos");
    }

    @Test
    void resetDesbloquea() {
        String ip = "10.0.0.1";
        for (int i = 0; i < 10; i++) {
            limiter.recordFailedAttempt(ip);
        }
        assertTrue(limiter.isBlocked(ip));

        limiter.resetAttempts(ip);
        assertFalse(limiter.isBlocked(ip));
    }

    @Test
    void ipsDiferentesIndependientes() {
        for (int i = 0; i < 10; i++) {
            limiter.recordFailedAttempt("1.1.1.1");
        }
        assertTrue(limiter.isBlocked("1.1.1.1"));
        assertFalse(limiter.isBlocked("2.2.2.2"));
    }
}

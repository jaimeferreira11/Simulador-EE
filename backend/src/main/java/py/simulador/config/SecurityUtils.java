package py.simulador.config;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utilidad para extraer datos del usuario autenticado desde el SecurityContext.
 * El JwtFilter coloca el Claims como principal en la autenticación.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /** ID del usuario autenticado (subject del JWT) */
    public static Long getUserId() {
        return Long.valueOf(getClaims().getSubject());
    }

    /** Rol del usuario autenticado (claim 'rol' del JWT) */
    public static String getRol() {
        return getClaims().get("rol", String.class);
    }

    /**
     * Rol del usuario autenticado, o {@code null} si no hay un contexto de seguridad
     * con Claims (p.ej. invocaciones internas/tests sin SecurityContext). No lanza.
     */
    public static String getRolOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Claims claims)) {
            return null;
        }
        return claims.get("rol", String.class);
    }

    /** Email del usuario autenticado */
    public static String getEmail() {
        return getClaims().get("email", String.class);
    }

    private static Claims getClaims() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Claims)) {
            throw new IllegalStateException("No hay usuario autenticado en el SecurityContext");
        }
        return (Claims) auth.getPrincipal();
    }
}

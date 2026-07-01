package py.simulador.usuario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import py.simulador.IntegrationTestBase;
import py.simulador.api.generated.model.LoginRequest;
import py.simulador.api.generated.model.TokenResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cubre la matriz de autorización de PATCH /usuarios/{id} ("propio o ADMIN"):
 * - Un usuario solo puede actualizar su propio perfil.
 * - ADMIN_PLATAFORMA puede actualizar el perfil de cualquiera.
 * - Cualquier otro caso (incluido MODERADOR sobre otro usuario) -> 403.
 * - rol_codigo se ignora en update (sin vector de elevación de privilegios).
 *
 * Los ids del seed se asignan por secuencia, así que se resuelven dinámicamente
 * vía /auth/me en lugar de hardcodearlos.
 */
class UsuarioUpdateAuthTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate rest;

    private String adminToken;
    private String moderadorToken;
    private String jugadorToken;

    private long adminId;
    private long moderadorId;
    private long jugadorId;

    @BeforeEach
    void login() {
        adminToken = loginAs("admin@simulador.py");
        moderadorToken = loginAs("moderador@simulador.py");
        jugadorToken = loginAs("capitan1@simulador.py"); // seed JUGADOR

        adminId = meId(adminToken);
        moderadorId = meId(moderadorToken);
        jugadorId = meId(jugadorToken);
    }

    private String loginAs(String email) {
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("password123");
        TokenResponse tokens = rest.postForObject("/auth/login", login, TokenResponse.class);
        assertNotNull(tokens, "Login should return tokens for " + email);
        return tokens.getAccessToken();
    }

    @SuppressWarnings("unchecked")
    private long meId(String token) {
        ResponseEntity<Map> resp = rest.exchange("/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers(token)), Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        return ((Number) resp.getBody().get("id")).longValue();
    }

    private HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) {
            h.setBearerAuth(token);
        }
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> patch(String token, long id, String jsonBody) {
        return rest.exchange("/usuarios/" + id, HttpMethod.PATCH,
                new HttpEntity<>(jsonBody, headers(token)), Map.class);
    }

    private String nombreBody(String nombre) {
        return "{\"nombre_completo\":\"%s\"}".formatted(nombre);
    }

    // ---------- propio ----------

    @Test
    void jugadorCanUpdateOwnProfile() {
        ResponseEntity<Map> resp = patch(jugadorToken, jugadorId, nombreBody("Ana B. Editado"));
        assertEquals(HttpStatus.OK, resp.getStatusCode(),
                "JUGADOR debe poder editar su propio perfil");
        assertEquals("Ana B. Editado", resp.getBody().get("nombre_completo"));
    }

    // ---------- ajeno ----------

    @Test
    void jugadorCannotUpdateOtherProfile() {
        ResponseEntity<Map> resp = patch(jugadorToken, moderadorId, nombreBody("Hackeado"));
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                "JUGADOR no debe poder editar el perfil de otro usuario");
    }

    @Test
    void moderadorCannotUpdateOtherProfile() {
        // Solo ADMIN bypassa la regla de propiedad; MODERADOR sobre otro -> 403.
        ResponseEntity<Map> resp = patch(moderadorToken, jugadorId, nombreBody("Editado por mod"));
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                "MODERADOR no debe poder editar el perfil de otro usuario (solo ADMIN)");
    }

    // ---------- ADMIN ----------

    @Test
    void adminCanUpdateOtherProfile() {
        ResponseEntity<Map> resp = patch(adminToken, jugadorId, nombreBody("Editado por admin"));
        assertEquals(HttpStatus.OK, resp.getStatusCode(),
                "ADMIN debe poder editar el perfil de cualquier usuario");
        assertEquals("Editado por admin", resp.getBody().get("nombre_completo"));
    }

    @Test
    void moderadorCanUpdateOwnProfile() {
        ResponseEntity<Map> resp = patch(moderadorToken, moderadorId, nombreBody("Prof. Editado"));
        assertEquals(HttpStatus.OK, resp.getStatusCode(),
                "MODERADOR debe poder editar su propio perfil");
    }

    // ---------- sin elevación de rol (rol_codigo ignorado) ----------

    @Test
    void rolCodigoIsIgnoredOnUpdate() {
        // El JUGADOR intenta auto-promoverse a ADMIN vía PATCH sobre su propio perfil.
        String body = "{\"nombre_completo\":\"Ana\",\"rol_codigo\":\"ADMIN_PLATAFORMA\"}";
        ResponseEntity<Map> resp = patch(jugadorToken, jugadorId, body);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        // El rol no debe cambiar: sigue siendo JUGADOR.
        assertEquals("JUGADOR", resp.getBody().get("rol"),
                "rol_codigo debe ignorarse en update: no debe haber elevación de privilegios");
    }
}

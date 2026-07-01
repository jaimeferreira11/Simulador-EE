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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cubre la matriz de autorización de POST /usuarios:
 * - Gate de autorización: solo ADMIN_PLATAFORMA o MODERADOR pueden invocarlo.
 * - Regla de negocio: MODERADOR solo puede crear usuarios con rol JUGADOR.
 */
class UsuarioControllerAuthTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate rest;

    private String adminToken;
    private String moderadorToken;
    private String jugadorToken;

    @BeforeEach
    void login() {
        adminToken = loginAs("admin@simulador.py");
        moderadorToken = loginAs("moderador@simulador.py");
        jugadorToken = loginAs("capitan1@simulador.py"); // seed JUGADOR
    }

    private String loginAs(String email) {
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("password123");
        TokenResponse tokens = rest.postForObject("/auth/login", login, TokenResponse.class);
        assertNotNull(tokens, "Login should return tokens for " + email);
        return tokens.getAccessToken();
    }

    private HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) {
            h.setBearerAuth(token);
        }
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String body(String rolCodigo) {
        String email = "u" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        return """
                {"email":"%s","password":"password123",
                 "nombre_completo":"Nuevo Usuario","rol_codigo":"%s"}""".formatted(email, rolCodigo);
    }

    private ResponseEntity<Map> post(String token, String rolCodigo) {
        return rest.exchange("/usuarios", HttpMethod.POST,
                new HttpEntity<>(body(rolCodigo), headers(token)), Map.class);
    }

    private ResponseEntity<Map> getList(String token) {
        // Se filtra por rol=JUGADOR (igual que la pantalla "Usuarios" del MODERADOR).
        return rest.exchange("/usuarios?rol=JUGADOR", HttpMethod.GET,
                new HttpEntity<>(headers(token)), Map.class);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> getListUnfiltered(String token) {
        // GET sin filtro de rol: antes devolvía 500 porque el usuario SYSTEM del seed
        // no tiene valor en el enum del DTO generado. Ahora se excluye en el servicio.
        return rest.exchange("/usuarios", HttpMethod.GET,
                new HttpEntity<>(headers(token)), Map.class);
    }

    private ResponseEntity<Map> delete(String token, long id) {
        return rest.exchange("/usuarios/" + id, HttpMethod.DELETE,
                new HttpEntity<>(headers(token)), Map.class);
    }

    /** Crea un usuario (como ADMIN) y devuelve su id para ejercitar el DELETE. */
    private long createJugadorAsAdmin() {
        ResponseEntity<Map> resp = post(adminToken, "JUGADOR");
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        return ((Number) resp.getBody().get("id")).longValue();
    }

    // ---------- GET /usuarios (listar) : solo staff ----------

    @Test
    void jugadorCannotListUsuarios() {
        ResponseEntity<Map> resp = getList(jugadorToken);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void moderadorCanListUsuarios() {
        ResponseEntity<Map> resp = getList(moderadorToken);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void adminCanListUsuarios() {
        ResponseEntity<Map> resp = getList(adminToken);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminListUnfilteredReturns200WithoutSystemUser() {
        // Sin filtro de rol: debe ser 200 (no 500) y NO contener el usuario SYSTEM del seed.
        ResponseEntity<Map> resp = getListUnfiltered(adminToken);
        assertEquals(HttpStatus.OK, resp.getStatusCode(),
                "GET /usuarios sin filtro debe devolver 200, no 500");

        java.util.List<Map<String, Object>> content =
                (java.util.List<Map<String, Object>>) resp.getBody().get("content");
        assertNotNull(content, "La respuesta debe traer 'content'");
        boolean hasSystem = content.stream()
                .anyMatch(u -> "SYSTEM".equals(u.get("rol"))
                        || "system-bot@simulador.local".equals(u.get("email")));
        assertFalse(hasSystem, "El usuario SYSTEM no debe aparecer en la lista de usuarios");
    }

    // ---------- DELETE /usuarios/{id} : solo ADMIN ----------

    @Test
    void adminCanDeleteUsuario() {
        long id = createJugadorAsAdmin();
        ResponseEntity<Map> resp = delete(adminToken, id);
        assertTrue(resp.getStatusCode() == HttpStatus.NO_CONTENT
                        || resp.getStatusCode() == HttpStatus.OK,
                "ADMIN debe poder eliminar, got: " + resp.getStatusCode());
    }

    @Test
    void moderadorCannotDeleteUsuario() {
        long id = createJugadorAsAdmin();
        ResponseEntity<Map> resp = delete(moderadorToken, id);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void jugadorCannotDeleteUsuario() {
        long id = createJugadorAsAdmin();
        ResponseEntity<Map> resp = delete(jugadorToken, id);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    // ---------- MODERADOR ----------

    @Test
    void moderadorCanCreateJugador() {
        ResponseEntity<Map> resp = post(moderadorToken, "JUGADOR");
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals("JUGADOR", resp.getBody().get("rol"));
    }

    @Test
    void moderadorCannotCreateModerador() {
        ResponseEntity<Map> resp = post(moderadorToken, "MODERADOR");
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void moderadorCannotCreateAdmin() {
        ResponseEntity<Map> resp = post(moderadorToken, "ADMIN_PLATAFORMA");
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    // ---------- ADMIN ----------

    @Test
    void adminCanCreateModerador() {
        ResponseEntity<Map> resp = post(adminToken, "MODERADOR");
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals("MODERADOR", resp.getBody().get("rol"));
    }

    @Test
    void adminCanCreateJugador() {
        ResponseEntity<Map> resp = post(adminToken, "JUGADOR");
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals("JUGADOR", resp.getBody().get("rol"));
    }

    // ---------- JUGADOR / anónimo ----------

    @Test
    void jugadorCannotCallEndpoint() {
        ResponseEntity<Map> resp = post(jugadorToken, "JUGADOR");
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void unauthenticatedIsRejected() {
        ResponseEntity<Map> resp = post(null, "JUGADOR");
        // El stack JWT stateless de la app no configura un AuthenticationEntryPoint propio,
        // por lo que Spring Security rechaza al anónimo con 403 (default Http403ForbiddenEntryPoint).
        // Convención del codebase: validar que el anónimo es rechazado (4xx), nunca creado.
        assertTrue(resp.getStatusCode().is4xxClientError(),
                "Unauthenticated POST /usuarios must be rejected, got: " + resp.getStatusCode());
        assertNotEquals(HttpStatus.CREATED, resp.getStatusCode());
    }
}

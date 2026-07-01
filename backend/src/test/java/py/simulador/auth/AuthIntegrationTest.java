package py.simulador.auth;

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
 * Integration test for auth endpoints against real PostgreSQL.
 * Uses the seed data from V202604211012__seed_dev_data.sql.
 */
class AuthIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void loginConCredencialesValidas() {
        // The dev seed creates a moderador: moderador@simulador.py / password123
        LoginRequest request = new LoginRequest();
        request.setEmail("moderador@simulador.py");
        request.setPassword("password123");

        ResponseEntity<TokenResponse> response = rest.postForEntity(
                "/auth/login", request, TokenResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getAccessToken());
        assertNotNull(response.getBody().getRefreshToken());
    }

    @Test
    void loginConPasswordIncorrecto() {
        LoginRequest request = new LoginRequest();
        request.setEmail("moderador@simulador.py");
        request.setPassword("wrong-password");

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = rest.postForEntity(
                "/auth/login", request, Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void authMeConToken() {
        // Login first
        LoginRequest login = new LoginRequest();
        login.setEmail("moderador@simulador.py");
        login.setPassword("password123");
        TokenResponse tokens = rest.postForObject("/auth/login", login, TokenResponse.class);
        assertNotNull(tokens);

        // Call /auth/me with the token
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.getAccessToken());
        ResponseEntity<Map> response = rest.exchange(
                "/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("moderador@simulador.py", response.getBody().get("email"));
    }

    @Test
    void refreshToken() {
        // Login
        LoginRequest login = new LoginRequest();
        login.setEmail("moderador@simulador.py");
        login.setPassword("password123");
        TokenResponse tokens = rest.postForObject("/auth/login", login, TokenResponse.class);
        assertNotNull(tokens);

        // Refresh
        Map<String, String> refreshReq = Map.of("refresh_token", tokens.getRefreshToken());
        ResponseEntity<TokenResponse> response = rest.postForEntity(
                "/auth/refresh", refreshReq, TokenResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getAccessToken());
        // New refresh token (rotation)
        assertNotEquals(tokens.getRefreshToken(), response.getBody().getRefreshToken());
    }

    @Test
    void endpointProtegidoSinToken() {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = rest.getForEntity("/competencias", Map.class);

        // Should be 401 or 403
        assertTrue(response.getStatusCode().is4xxClientError());
    }
}

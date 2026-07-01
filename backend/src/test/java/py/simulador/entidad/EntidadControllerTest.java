package py.simulador.entidad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import py.simulador.IntegrationTestBase;
import py.simulador.api.generated.model.LoginRequest;
import py.simulador.api.generated.model.TokenResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntidadControllerTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate rest;

    private String obtenerToken() {
        LoginRequest login = new LoginRequest();
        login.setEmail("moderador@simulador.py");
        login.setPassword("password123");
        TokenResponse tokens = rest.postForObject("/auth/login", login, TokenResponse.class);
        assertNotNull(tokens, "Login should return tokens");
        return tokens.getAccessToken();
    }

    @Test
    void listarEntidadesDevuelve200YArrayJson() {
        String token = obtenerToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<List> response = rest.exchange(
                "/entidades", HttpMethod.GET, entity, List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isEmpty(), "Seed data should provide at least one entidad");
    }

    @Test
    void listarEntidadesSinTokenDevuelve401() {
        ResponseEntity<String> response = rest.getForEntity("/entidades", String.class);

        assertTrue(response.getStatusCode().is4xxClientError(),
                "Endpoint should require authentication");
    }
}

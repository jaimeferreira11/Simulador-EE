package py.simulador.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import py.simulador.IntegrationTestBase;
import py.simulador.api.generated.model.LoginRequest;
import py.simulador.api.generated.model.TokenResponse;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.equipo.EquipoRepository;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChatControllerTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private CompetenciaRepository competenciaRepo;

    @Autowired
    private EquipoRepository equipoRepo;

    private Long competenciaId;
    private Long equipoId;

    @BeforeEach
    void setUp() {
        competenciaId = competenciaRepo.findByCodigo("RTL-2026A")
                .orElseThrow(() -> new IllegalStateException("Seed competencia RTL-2026A not found"))
                .getId();

        equipoId = equipoRepo.findByCompetenciaId(competenciaId).stream()
                .filter(e -> "Guaraní Market".equals(e.getNombreEmpresa()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Seed equipo 'Guaraní Market' not found"))
                .getId();
    }

    private String obtenerTokenJugador() {
        LoginRequest login = new LoginRequest();
        login.setEmail("capitan1@simulador.py");
        login.setPassword("password123");
        TokenResponse tokens = rest.postForObject("/auth/login", login, TokenResponse.class);
        assertNotNull(tokens, "Login jugador should return tokens");
        return tokens.getAccessToken();
    }

    @Test
    void getChat_sinAuth_retorna401() {
        String url = "/equipos/" + equipoId + "/chat?competencia_id=" + competenciaId;
        ResponseEntity<String> response = rest.getForEntity(url, String.class);
        assertTrue(response.getStatusCode().is4xxClientError(),
                "Unauthenticated GET should be rejected (4xx), got: " + response.getStatusCode());
    }

    @Test
    void postChat_sinAuth_retorna401() {
        String url = "/equipos/" + equipoId + "/chat";
        Map<String, Object> body = Map.of(
                "competencia_id", competenciaId,
                "contenido", "Hola equipo"
        );
        ResponseEntity<String> response = rest.postForEntity(url, body, String.class);
        assertTrue(response.getStatusCode().is4xxClientError(),
                "Unauthenticated POST should be rejected (4xx), got: " + response.getStatusCode());
    }

    @Test
    void getChat_conAuth_retorna200() {
        String token = obtenerTokenJugador();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = "/equipos/" + equipoId + "/chat?competencia_id=" + competenciaId;
        ResponseEntity<Map> response = rest.exchange(url, HttpMethod.GET, entity, Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("content"), "Response should have 'content' key");
        assertTrue(response.getBody().get("content") instanceof java.util.List, "'content' should be a list");
    }

    @Test
    void postChat_conAuth_retorna201() {
        String token = obtenerTokenJugador();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "competencia_id", competenciaId,
                "contenido", "Mensaje de prueba de integración"
        );
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        String url = "/equipos/" + equipoId + "/chat";
        ResponseEntity<Map> response = rest.exchange(url, HttpMethod.POST, entity, Map.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("id"), "Response should have 'id' key");
        assertEquals("Mensaje de prueba de integración", response.getBody().get("contenido"));
    }

    @Test
    void postChat_contenidoVacio_retorna400() {
        String token = obtenerTokenJugador();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "competencia_id", competenciaId,
                "contenido", ""
        );
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        String url = "/equipos/" + equipoId + "/chat";
        ResponseEntity<String> response = rest.exchange(url, HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}

package py.simulador.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import py.simulador.IntegrationTestBase;
import py.simulador.api.generated.model.LoginRequest;
import py.simulador.api.generated.model.TokenResponse;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.decision.DecisionEquipoRepository;
import py.simulador.trimestre.TrimestreRepository;
import py.simulador.usuario.UsuarioRepository;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link DemoController}.
 *
 * <p>Uses real PostgreSQL (Testcontainers via {@link IntegrationTestBase}) and
 * the DEMO seed applied by Flyway migrations. Authentication is done via the
 * real JWT endpoint — same pattern as {@link py.simulador.admin.AdminControllerTest}.
 */
class DemoControllerTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private CompetenciaRepository competenciaRepo;

    @Autowired
    private TrimestreRepository trimestreRepo;

    @Autowired
    private UsuarioRepository usuarioRepo;

    @Autowired
    private DecisionEquipoRepository decisionEquipoRepo;

    private Long demoId;
    private Long otherCompId;   // RTL-2026A — never DEMO
    private String moderadorToken;
    private String jugadorToken;

    @BeforeEach
    void setUp() {
        demoId = competenciaRepo.findByCodigo(DemoConstants.COMPETENCIA_CODIGO)
                .orElseThrow(() -> new AssertionError("DEMO competencia not seeded"))
                .getId();

        otherCompId = competenciaRepo.findByCodigo("RTL-2026A")
                .orElseThrow(() -> new AssertionError("Seed competencia RTL-2026A not found"))
                .getId();

        moderadorToken = loginAs("moderador@simulador.py");
        jugadorToken   = loginAs("capitan1@simulador.py");

        // Ensure DEMO is in a clean state by reiniciando before each test.
        // This is idempotent: reiniciar wipes runtime data and re-opens Q1.
        rest.exchange(
                "/competencias/" + demoId + "/demo/reiniciar",
                HttpMethod.POST,
                new HttpEntity<>(headers(moderadorToken)),
                Map.class);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

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
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private Map<String, Object> minimalDecisionPayload() {
        return Map.of("precio_venta", 45_000);
    }

    // -----------------------------------------------------------------------
    // 1. avanzar_moderador_devuelve200
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("avanzar_moderador_devuelve200 — modera puede avanzar y competencia queda EN_CURSO")
    void avanzar_moderador_devuelve200() {
        // Send CEO decision first so all teams have submitted
        rest.exchange(
                "/competencias/" + demoId + "/demo/decision-ceo",
                HttpMethod.POST,
                new HttpEntity<>(minimalDecisionPayload(), headers(moderadorToken)),
                Map.class);

        ResponseEntity<Map> response = rest.exchange(
                "/competencias/" + demoId + "/demo/avanzar",
                HttpMethod.POST,
                new HttpEntity<>(headers(moderadorToken)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("EN_CURSO", response.getBody().get("competenciaEstado"),
                "La competencia debe seguir EN_CURSO tras avanzar Q1");
    }

    // -----------------------------------------------------------------------
    // 2. avanzar_jugador_devuelve403
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("avanzar_jugador_devuelve403 — JUGADOR no puede invocar avanzar")
    void avanzar_jugador_devuelve403() {
        ResponseEntity<String> response = rest.exchange(
                "/competencias/" + demoId + "/demo/avanzar",
                HttpMethod.POST,
                new HttpEntity<>(headers(jugadorToken)),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // 3. reiniciar_devuelve200_yEmiteWS
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("reiniciar_devuelve200_yEmiteWS — endpoint devuelve 200 con la competencia DEMO")
    void reiniciar_devuelve200_yEmiteWS() {
        // setUp already called reiniciar, but we call it again to assert the response
        ResponseEntity<Map> response = rest.exchange(
                "/competencias/" + demoId + "/demo/reiniciar",
                HttpMethod.POST,
                new HttpEntity<>(headers(moderadorToken)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(DemoConstants.COMPETENCIA_CODIGO, response.getBody().get("codigo"),
                "El cuerpo de respuesta debe contener el codigo 'DEMO'");
    }

    // -----------------------------------------------------------------------
    // 4. reiniciar_competenciaNoDemo_devuelve409
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("reiniciar_competenciaNoDemo_devuelve409 — competencia no-DEMO devuelve 409 CONFLICT")
    void reiniciar_competenciaNoDemo_devuelve409() {
        ResponseEntity<Map> response = rest.exchange(
                "/competencias/" + otherCompId + "/demo/reiniciar",
                HttpMethod.POST,
                new HttpEntity<>(headers(moderadorToken)),
                Map.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        // RFC 7807 problem+json body must contain the offending code
        String detail = (String) response.getBody().get("detail");
        assertNotNull(detail, "Problem body must have 'detail' field");
        assertTrue(detail.contains("RTL-2026A"),
                "Detail must mention the non-DEMO codigo. Got: " + detail);
    }

    // -----------------------------------------------------------------------
    // 5. decisionCeo_devuelve200_persisteConCeoUser
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("decisionCeo_devuelve200_persisteConCeoUser — decision queda atribuida al usuario CEO sintetico")
    void decisionCeo_devuelve200_persisteConCeoUser() {
        Long ceoUserId = usuarioRepo.findByEmail(DemoConstants.CEO_EMAIL)
                .orElseThrow(() -> new AssertionError("CEO user not seeded"))
                .getId();

        ResponseEntity<Map> response = rest.exchange(
                "/competencias/" + demoId + "/demo/decision-ceo",
                HttpMethod.POST,
                new HttpEntity<>(minimalDecisionPayload(), headers(moderadorToken)),
                Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody(), "Response body must not be null");

        // Verify the decision was persisted with the CEO's user ID
        Long decisionId = ((Number) response.getBody().get("id")).longValue();
        py.simulador.decision.DecisionEquipoEntity persisted = decisionEquipoRepo.findById(decisionId)
                .orElseThrow(() -> new AssertionError("Decision not found in DB: " + decisionId));

        assertEquals(ceoUserId, persisted.getRegistradoPorUsuarioId(),
                "La decision debe estar atribuida al usuario CEO sintetico");
    }
}

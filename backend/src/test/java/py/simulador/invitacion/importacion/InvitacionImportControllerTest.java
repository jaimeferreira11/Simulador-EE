package py.simulador.invitacion.importacion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import py.simulador.IntegrationTestBase;
import py.simulador.api.generated.model.LoginRequest;
import py.simulador.api.generated.model.TokenResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cubre la invitación masiva por CSV de POST /equipos/{equipoId}/invitaciones/import:
 * - Gate de autorización (MODERADOR/ADMIN sí, JUGADOR no).
 * - Éxito parcial real: una fila mala no aborta el resto (aislamiento por transacción).
 * - Reutilización de las validaciones de la invitación individual (ya en otro equipo
 *   de la misma competencia, email faltante, etc.) sin reimplementarlas.
 */
class InvitacionImportControllerTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    private String moderadorToken;
    private String jugadorToken;
    private Long equipoGuaraniId; // equipo destino (Guaraní Market, competencia RTL-2026A)

    @BeforeEach
    void setUp() {
        moderadorToken = loginAs("moderador@simulador.py");
        jugadorToken = loginAs("capitan1@simulador.py");
        equipoGuaraniId = jdbc.queryForObject(
                "SELECT id FROM sim.equipo WHERE nombre_empresa = 'Guaraní Market'", Long.class);
    }

    private String loginAs(String email) {
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("password123");
        TokenResponse tokens = rest.postForObject("/auth/login", login, TokenResponse.class);
        assertNotNull(tokens, "Login should return tokens for " + email);
        return tokens.getAccessToken();
    }

    private String uniqueEmail() {
        return "inv" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }

    private int pendientes(String email) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sim.invitacion WHERE email = ? AND estado = 'PENDIENTE'",
                Integer.class, email);
        return n == null ? 0 : n;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> importCsv(String token, Long equipoId, String csv) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ByteArrayResource filePart = new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "miembros.csv";
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);
        return rest.exchange("/equipos/" + equipoId + "/invitaciones/import", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
    }

    // ---------- Lote válido de 3 filas ----------

    @Test
    @SuppressWarnings("unchecked")
    void moderadorImportsThreeValidRows() {
        String e1 = uniqueEmail(), e2 = uniqueEmail(), e3 = uniqueEmail();
        String csv = "email,nombre_completo\n"
                + e1 + ",Uno Test\n"
                + e2 + ",Dos Test\n"
                + e3 + ",Tres Test\n";

        ResponseEntity<Map> resp = importCsv(moderadorToken, equipoGuaraniId, csv);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> b = resp.getBody();
        assertNotNull(b);
        assertEquals(3, b.get("total"));
        assertEquals(3, ((List<?>) b.get("invitados")).size());
        assertEquals(0, ((List<?>) b.get("errores")).size());

        // Las invitaciones (y sus tokens) se crearon de verdad.
        assertEquals(1, pendientes(e1));
        assertEquals(1, pendientes(e2));
        assertEquals(1, pendientes(e3));
        Map<String, Object> inv = jdbc.queryForMap(
                "SELECT token, equipo_id FROM sim.invitacion WHERE email = ?", e1);
        assertNotNull(inv.get("token"));
        assertEquals(equipoGuaraniId, ((Number) inv.get("equipo_id")).longValue());
    }

    // ---------- Éxito parcial: jugador ya en OTRO equipo de la misma competencia ----------

    @Test
    @SuppressWarnings("unchecked")
    void rowAlreadyInAnotherTeamIsReportedButOthersInvited() {
        String e1 = uniqueEmail(), e3 = uniqueEmail();
        // capitan2 ya es miembro de Ñandutí Express en la MISMA competencia RTL-2026A.
        String yaEnOtroEquipo = "capitan2@simulador.py";
        String csv = "email,nombre_completo\n"
                + e1 + ",Fila Uno\n"
                + yaEnOtroEquipo + ",Capitan Dos\n"
                + e3 + ",Fila Tres\n";

        ResponseEntity<Map> resp = importCsv(moderadorToken, equipoGuaraniId, csv);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> b = resp.getBody();
        assertEquals(3, b.get("total"));

        List<Map<String, Object>> invitados = (List<Map<String, Object>>) b.get("invitados");
        List<Map<String, Object>> errores = (List<Map<String, Object>>) b.get("errores");
        assertEquals(2, invitados.size(), "filas 1 y 3 deben invitarse");
        assertEquals(1, errores.size(), "fila 2 debe fallar");
        assertEquals(2, errores.get(0).get("fila"));
        assertEquals(yaEnOtroEquipo, errores.get(0).get("email"));

        assertEquals(1, pendientes(e1));
        assertEquals(1, pendientes(e3));
        assertEquals(0, pendientes(yaEnOtroEquipo), "no se crea invitacion para el conflictivo");
    }

    // ---------- Éxito parcial: fila malformada (sin email) ----------

    @Test
    @SuppressWarnings("unchecked")
    void malformedRowIsReportedButOthersInvited() {
        String e1 = uniqueEmail(), e3 = uniqueEmail();
        String csv = "email,nombre_completo\n"
                + e1 + ",Fila Uno\n"
                + ",Fila Sin Email\n"
                + e3 + ",Fila Tres\n";

        ResponseEntity<Map> resp = importCsv(moderadorToken, equipoGuaraniId, csv);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> b = resp.getBody();
        List<Map<String, Object>> invitados = (List<Map<String, Object>>) b.get("invitados");
        List<Map<String, Object>> errores = (List<Map<String, Object>>) b.get("errores");
        assertEquals(2, invitados.size());
        assertEquals(1, errores.size());
        assertEquals(2, errores.get(0).get("fila"));
        assertEquals(1, pendientes(e1));
        assertEquals(1, pendientes(e3));
    }

    // ---------- Idempotencia: invitar dos veces el mismo email no duplica ----------

    @Test
    @SuppressWarnings("unchecked")
    void duplicatePendingInvitationIsCountedAsInvitedNotDuplicated() {
        String e1 = uniqueEmail();
        String csv = "email,nombre_completo\n" + e1 + ",Una Vez\n";

        importCsv(moderadorToken, equipoGuaraniId, csv);
        ResponseEntity<Map> resp = importCsv(moderadorToken, equipoGuaraniId, csv);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> b = resp.getBody();
        assertEquals(1, ((List<?>) b.get("invitados")).size());
        assertEquals(0, ((List<?>) b.get("errores")).size());
        assertEquals(1, pendientes(e1), "no se duplica la invitacion pendiente");
    }

    // ---------- Autorización: JUGADOR no puede ----------

    @Test
    void jugadorCannotImport() {
        String csv = "email,nombre_completo\n" + uniqueEmail() + ",X\n";
        ResponseEntity<Map> resp = importCsv(jugadorToken, equipoGuaraniId, csv);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    // ---------- Límite de filas ----------

    @Test
    void overLimitCsvIsRejected() {
        StringBuilder csv = new StringBuilder("email,nombre_completo\n");
        for (int i = 0; i < InvitacionImportService.MAX_FILAS + 1; i++) {
            csv.append(uniqueEmail()).append(",Fila ").append(i).append("\n");
        }
        ResponseEntity<Map> resp = importCsv(moderadorToken, equipoGuaraniId, csv.toString());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resp.getStatusCode());
    }
}

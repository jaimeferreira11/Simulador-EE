package py.simulador.invitacion;

import org.junit.jupiter.api.AfterEach;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cubre el endurecimiento de la invitación individual y por lote:
 *
 * <p>Gap A — Autorización por rol + propiedad de la competencia:
 * <ul>
 *   <li>MODERADOR dueño de la competencia invita -> 201.</li>
 *   <li>MODERADOR que NO es dueño -> 403 (individual y por lote).</li>
 *   <li>ADMIN_PLATAFORMA (no dueño) -> 201 (bypass).</li>
 *   <li>JUGADOR -> 403 (gate de rol).</li>
 * </ul>
 *
 * <p>Gap B — Capacidad de equipo configurable por competencia
 * ({@code competencia.max_integrantes_equipo}):
 * <ul>
 *   <li>Con límite N: invitar hasta N funciona; el (N+1)-ésimo se rechaza
 *       (individual -> 4xx; lote -> fila en {@code errores}).</li>
 *   <li>Con límite NULL: ilimitado.</li>
 * </ul>
 *
 * <p>Las competencias/equipos/usuarios extra se crean y eliminan en cada test
 * para no contaminar los datos sembrados ni otros tests (el contenedor se comparte).
 */
class InvitacionAuthCapacityTest extends IntegrationTestBase {

    private static final String BCRYPT_PASSWORD123 =
            "$2a$06$fIUZIKdJwfwtGFdh4X6Xg.BofMiHuhtKZRuTteldZsx016ITfbDB6";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    private String adminToken;
    private String moderadorDuenoToken;   // moderador@simulador.py, dueño de RTL-2026A
    private String jugadorToken;

    private String suffix;
    private Long otroModeradorId;
    private Long compAjenaId;     // competencia cuyo dueño es OTRO moderador
    private Long equipoAjenoId;   // equipo de esa competencia ajena
    private Long otroModeradorUsuarioCreado; // para limpieza

    @BeforeEach
    void setUp() {
        adminToken = loginAs("admin@simulador.py");
        moderadorDuenoToken = loginAs("moderador@simulador.py");
        jugadorToken = loginAs("capitan1@simulador.py");

        suffix = UUID.randomUUID().toString().substring(0, 8);

        // Crea un SEGUNDO moderador y una competencia+equipo cuya propiedad es de él.
        Long rolModerador = jdbc.queryForObject(
                "SELECT id FROM sim.rol_usuario WHERE codigo = 'MODERADOR'", Long.class);
        otroModeradorId = jdbc.queryForObject(
                "INSERT INTO sim.usuario (email, password_hash, nombre_completo, rol_usuario_id, activo, email_verificado) "
                        + "VALUES (?, ?, 'Otro Moderador', ?, TRUE, TRUE) RETURNING id",
                Long.class, "mod-" + suffix + "@simulador.py", BCRYPT_PASSWORD123, rolModerador);
        otroModeradorUsuarioCreado = otroModeradorId;

        compAjenaId = insertCompetencia("AJN-" + suffix, otroModeradorId, null);
        equipoAjenoId = insertEquipo(compAjenaId, "Equipo Ajeno " + suffix);
    }

    @AfterEach
    void tearDown() {
        // Orden inverso a la creación; ON DELETE RESTRICT en las FKs.
        jdbc.update("DELETE FROM sim.invitacion WHERE equipo_id IN "
                + "(SELECT id FROM sim.equipo WHERE competencia_id IN "
                + "(SELECT id FROM sim.competencia WHERE codigo LIKE ?))", "%-" + suffix);
        jdbc.update("DELETE FROM sim.equipo WHERE competencia_id IN "
                + "(SELECT id FROM sim.competencia WHERE codigo LIKE ?)", "%-" + suffix);
        jdbc.update("DELETE FROM sim.competencia WHERE codigo LIKE ?", "%-" + suffix);
        if (otroModeradorUsuarioCreado != null) {
            jdbc.update("DELETE FROM sim.usuario WHERE id = ?", otroModeradorUsuarioCreado);
        }
    }

    // ===================== Gap A: autorización + propiedad =====================

    @Test
    void duenoCanInviteSingle() {
        ResponseEntity<Map> resp = invitarSingle(moderadorDuenoToken, equipoGuaraniId(), uniqueEmail());
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    @Test
    void nonOwnerModeradorForbiddenSingle() {
        // moderador@simulador.py NO es dueño de la competencia ajena -> 403
        ResponseEntity<Map> resp = invitarSingle(moderadorDuenoToken, equipoAjenoId, uniqueEmail());
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void nonOwnerModeradorForbiddenImport() {
        String csv = "email,nombre_completo\n" + uniqueEmail() + ",X\n";
        ResponseEntity<Map> resp = importCsv(moderadorDuenoToken, equipoAjenoId, csv);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void adminBypassesOwnershipSingle() {
        // admin no es dueño de la competencia ajena, pero el chequeo de propiedad lo omite.
        ResponseEntity<Map> resp = invitarSingle(adminToken, equipoAjenoId, uniqueEmail());
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    }

    @Test
    void jugadorForbiddenSingle() {
        ResponseEntity<Map> resp = invitarSingle(jugadorToken, equipoGuaraniId(), uniqueEmail());
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    // ===================== Gap B: capacidad de equipo =====================

    @Test
    @SuppressWarnings("unchecked")
    void capacityLimitRejectsExtraSingle() {
        // Competencia con límite = 2, equipo vacío: 2 invitaciones OK, la 3ª rechazada.
        Long compId = insertCompetencia("CAP-" + suffix, otroModeradorId, (short) 2);
        Long equipoId = insertEquipo(compId, "Equipo Cap " + suffix);

        assertEquals(HttpStatus.CREATED,
                invitarSingle(adminToken, equipoId, uniqueEmail()).getStatusCode());
        assertEquals(HttpStatus.CREATED,
                invitarSingle(adminToken, equipoId, uniqueEmail()).getStatusCode());

        ResponseEntity<Map> tercera = invitarSingle(adminToken, equipoId, uniqueEmail());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, tercera.getStatusCode(),
                "la 3ª invitación supera el límite de 2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void capacityLimitReportsOverCapacityRowInImport() {
        // Límite = 1: la 1ª fila se invita, la 2ª va a errores (lote continúa).
        Long compId = insertCompetencia("CIM-" + suffix, otroModeradorId, (short) 1);
        Long equipoId = insertEquipo(compId, "Equipo CapImport " + suffix);

        String e1 = uniqueEmail(), e2 = uniqueEmail();
        String csv = "email,nombre_completo\n" + e1 + ",Uno\n" + e2 + ",Dos\n";
        ResponseEntity<Map> resp = importCsv(adminToken, equipoId, csv);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> b = resp.getBody();
        assertNotNull(b);
        assertEquals(2, b.get("total"));
        assertEquals(1, ((java.util.List<?>) b.get("invitados")).size(), "solo cabe 1");
        assertEquals(1, ((java.util.List<?>) b.get("errores")).size(), "la 2ª supera el límite");
    }

    @Test
    void nullLimitIsUnlimitedSingle() {
        // Competencia ajena tiene max_integrantes_equipo NULL: sin rechazo por cupo.
        for (int i = 0; i < 5; i++) {
            assertEquals(HttpStatus.CREATED,
                    invitarSingle(adminToken, equipoAjenoId, uniqueEmail()).getStatusCode());
        }
    }

    // ===================== helpers =====================

    private Long equipoGuaraniId() {
        return jdbc.queryForObject(
                "SELECT id FROM sim.equipo WHERE nombre_empresa = 'Guaraní Market'", Long.class);
    }

    private Long insertCompetencia(String codigo, Long moderadorId, Short maxIntegrantes) {
        return jdbc.queryForObject(
                "INSERT INTO sim.competencia (codigo, nombre, rubro_id, parametro_macro_id, parametro_rubro_id, "
                        + "moderador_id, entidad_id, num_trimestres, num_equipos_max, max_integrantes_equipo, "
                        + "caja_inicial, capacidad_inicial, headcount_inicial, salario_inicial, "
                        + "inventario_inicial, valor_planta_inicial, estado) "
                        + "VALUES (?, ?, "
                        + "(SELECT id FROM sim.rubro WHERE codigo = 'RETAIL_CONV'), "
                        + "(SELECT id FROM sim.parametro_macro WHERE nombre_set = 'PY_2026_BASE'), "
                        + "(SELECT id FROM sim.parametro_rubro WHERE codigo = 'RETAIL_CONV_BASE_2026'), "
                        + "?, (SELECT id FROM sim.entidad ORDER BY id LIMIT 1), "
                        + "4, 8, ?, 500000000, 50000, 100, 3500000, 0, 2500000000, 'BORRADOR') RETURNING id",
                Long.class, codigo, "Comp " + codigo, moderadorId,
                maxIntegrantes == null ? null : maxIntegrantes.intValue());
    }

    private Long insertEquipo(Long competenciaId, String nombre) {
        return jdbc.queryForObject(
                "INSERT INTO sim.equipo (competencia_id, nombre_empresa, codigo_color, estado) "
                        + "VALUES (?, ?, '#006B3F', 'ACTIVO') RETURNING id",
                Long.class, competenciaId, nombre);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> invitarSingle(String token, Long equipoId, String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        String body = "{\"email\":\"" + email + "\",\"nombreCompleto\":\"Test User\"}";
        return rest.exchange("/equipos/" + equipoId + "/invitaciones", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
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

    private String loginAs(String email) {
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("password123");
        TokenResponse tokens = rest.postForObject("/auth/login", login, TokenResponse.class);
        assertNotNull(tokens, "Login should return tokens for " + email);
        return tokens.getAccessToken();
    }

    private String uniqueEmail() {
        return "iac" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }
}

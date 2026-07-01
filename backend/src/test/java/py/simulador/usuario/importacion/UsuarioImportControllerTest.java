package py.simulador.usuario.importacion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import py.simulador.IntegrationTestBase;
import py.simulador.api.generated.model.LoginRequest;
import py.simulador.api.generated.model.TokenResponse;
import py.simulador.usuario.UsuarioRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cubre la importación masiva por CSV de POST /usuarios/import:
 * - Gate de autorización idéntico al alta individual (ADMIN/MODERADOR sí, JUGADOR no).
 * - Éxito parcial real: una fila mala no aborta el resto (aislamiento por transacción).
 * - Reutilización de la política de roles (MODERADOR solo puede importar JUGADOR).
 */
class UsuarioImportControllerTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UsuarioRepository usuarioRepo;

    private String adminToken;
    private String moderadorToken;
    private String jugadorToken;

    @BeforeEach
    void login() {
        adminToken = loginAs("admin@simulador.py");
        moderadorToken = loginAs("moderador@simulador.py");
        jugadorToken = loginAs("capitan1@simulador.py");
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
        return "imp" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> importCsv(String token, String csv) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ByteArrayResource filePart = new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "usuarios.csv";
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);
        return rest.exchange("/usuarios/import", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
    }

    // ---------- ADMIN: lote válido ----------

    @Test
    @SuppressWarnings("unchecked")
    void adminImportsThreeValidRows() {
        String e1 = uniqueEmail(), e2 = uniqueEmail(), e3 = uniqueEmail();
        String csv = "email,nombre_completo,rol\n"
                + e1 + ",Uno Test,JUGADOR\n"
                + e2 + ",Dos Test,JUGADOR\n"
                + e3 + ",Tres Test,JUGADOR\n";

        ResponseEntity<Map> resp = importCsv(adminToken, csv);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> b = resp.getBody();
        assertNotNull(b);
        assertEquals(3, b.get("total"));
        assertEquals(3, ((List<?>) b.get("creados")).size());
        assertEquals(0, ((List<?>) b.get("errores")).size());

        // Los usuarios existen realmente en DB
        assertTrue(usuarioRepo.findByEmail(e1).isPresent());
        assertTrue(usuarioRepo.findByEmail(e2).isPresent());
        assertTrue(usuarioRepo.findByEmail(e3).isPresent());
    }

    // ---------- Éxito parcial: fila duplicada ----------

    @Test
    @SuppressWarnings("unchecked")
    void duplicateRowIsReportedButOthersCreated() {
        String e1 = uniqueEmail(), e3 = uniqueEmail();
        String dup = "moderador@simulador.py"; // ya existe (seed)
        String csv = "email,nombre_completo\n"
                + e1 + ",Fila Uno\n"
                + dup + ",Fila Duplicada\n"
                + e3 + ",Fila Tres\n";

        ResponseEntity<Map> resp = importCsv(adminToken, csv);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> b = resp.getBody();
        assertEquals(3, b.get("total"));

        List<Map<String, Object>> creados = (List<Map<String, Object>>) b.get("creados");
        List<Map<String, Object>> errores = (List<Map<String, Object>>) b.get("errores");
        assertEquals(2, creados.size(), "filas 1 y 3 deben crearse");
        assertEquals(1, errores.size(), "fila 2 debe fallar");
        assertEquals(2, errores.get(0).get("fila"));
        assertEquals(dup, errores.get(0).get("email"));

        // Las filas buenas existen; el rol por defecto es JUGADOR
        assertTrue(usuarioRepo.findByEmail(e1).isPresent());
        assertTrue(usuarioRepo.findByEmail(e3).isPresent());
    }

    // ---------- Éxito parcial: fila malformada (sin email) ----------

    @Test
    @SuppressWarnings("unchecked")
    void malformedRowIsReportedButOthersCreated() {
        String e1 = uniqueEmail(), e3 = uniqueEmail();
        String csv = "email,nombre_completo\n"
                + e1 + ",Fila Uno\n"
                + ",Fila Sin Email\n"
                + e3 + ",Fila Tres\n";

        ResponseEntity<Map> resp = importCsv(adminToken, csv);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> b = resp.getBody();
        List<Map<String, Object>> creados = (List<Map<String, Object>>) b.get("creados");
        List<Map<String, Object>> errores = (List<Map<String, Object>>) b.get("errores");
        assertEquals(2, creados.size());
        assertEquals(1, errores.size());
        assertEquals(2, errores.get(0).get("fila"));
        assertTrue(usuarioRepo.findByEmail(e1).isPresent());
        assertTrue(usuarioRepo.findByEmail(e3).isPresent());
    }

    // ---------- Política de roles: MODERADOR importando un MODERADOR ----------

    @Test
    @SuppressWarnings("unchecked")
    void moderadorCannotImportNonJugadorRow() {
        String jug = uniqueEmail();
        String mod = uniqueEmail();
        String csv = "email,nombre_completo,rol\n"
                + jug + ",Jugador Ok,JUGADOR\n"
                + mod + ",Moderador No,MODERADOR\n";

        ResponseEntity<Map> resp = importCsv(moderadorToken, csv);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> b = resp.getBody();
        List<Map<String, Object>> creados = (List<Map<String, Object>>) b.get("creados");
        List<Map<String, Object>> errores = (List<Map<String, Object>>) b.get("errores");
        assertEquals(1, creados.size(), "la fila JUGADOR se crea");
        assertEquals(1, errores.size(), "la fila MODERADOR viola la politica");
        assertEquals(2, errores.get(0).get("fila"));

        assertTrue(usuarioRepo.findByEmail(jug).isPresent());
        assertTrue(usuarioRepo.findByEmail(mod).isEmpty(), "el MODERADOR no debe crearse");
    }

    // ---------- Autorización: JUGADOR no puede ----------

    @Test
    void jugadorCannotImport() {
        String csv = "email,nombre_completo\n" + uniqueEmail() + ",X\n";
        ResponseEntity<Map> resp = importCsv(jugadorToken, csv);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    // ---------- Límite de filas ----------

    @Test
    void overLimitCsvIsRejected() {
        StringBuilder csv = new StringBuilder("email,nombre_completo\n");
        for (int i = 0; i < UsuarioImportService.MAX_FILAS + 1; i++) {
            csv.append(uniqueEmail()).append(",Fila ").append(i).append("\n");
        }
        ResponseEntity<Map> resp = importCsv(adminToken, csv.toString());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, resp.getStatusCode());
    }
}

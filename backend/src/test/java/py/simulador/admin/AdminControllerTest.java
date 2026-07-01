package py.simulador.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import py.simulador.IntegrationTestBase;
import py.simulador.api.generated.model.LoginRequest;
import py.simulador.api.generated.model.TokenResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdminControllerTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate rest;

    private String adminToken;
    private String moderadorToken;

    @BeforeEach
    void login() {
        adminToken = loginAs("admin@simulador.py");
        moderadorToken = loginAs("moderador@simulador.py");
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
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // =====================================================================
    // ROLE ENFORCEMENT
    // =====================================================================

    @Nested
    class RoleEnforcement {

        @Test
        void adminCanAccessAdminEndpoints() {
            ResponseEntity<String> resp = rest.exchange(
                    "/admin/usuarios", HttpMethod.GET,
                    new HttpEntity<>(headers(adminToken)), String.class);
            assertEquals(HttpStatus.OK, resp.getStatusCode());
        }

        @Test
        void moderadorCannotAccessAdminEndpoints() {
            ResponseEntity<String> resp = rest.exchange(
                    "/admin/usuarios", HttpMethod.GET,
                    new HttpEntity<>(headers(moderadorToken)), String.class);
            assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        }

        @Test
        void unauthenticatedCannotAccessAdminEndpoints() {
            ResponseEntity<String> resp = rest.getForEntity("/admin/usuarios", String.class);
            assertTrue(resp.getStatusCode().is4xxClientError());
        }
    }

    // =====================================================================
    // USUARIOS ABM
    // =====================================================================

    @Nested
    class Usuarios {

        @Test
        void listUsuariosReturnsPaginatedResults() {
            ResponseEntity<Map> resp = rest.exchange(
                    "/admin/usuarios?page=0&size=5", HttpMethod.GET,
                    new HttpEntity<>(headers(adminToken)), Map.class);

            assertEquals(HttpStatus.OK, resp.getStatusCode());
            Map body = resp.getBody();
            assertNotNull(body);
            assertNotNull(body.get("content"));
            assertEquals(0, body.get("page"));
            assertEquals(5, body.get("size"));
            assertTrue((int) body.get("totalElements") > 0);
        }

        @Test
        void listUsuariosFilterByRol() {
            ResponseEntity<Map> resp = rest.exchange(
                    "/admin/usuarios?rol=MODERADOR", HttpMethod.GET,
                    new HttpEntity<>(headers(adminToken)), Map.class);

            assertEquals(HttpStatus.OK, resp.getStatusCode());
            List<?> content = (List<?>) resp.getBody().get("content");
            assertFalse(content.isEmpty());
            for (Object item : content) {
                Map<?, ?> user = (Map<?, ?>) item;
                assertEquals("MODERADOR", user.get("rol"));
            }
        }

        @Test
        void listUsuariosSearchByName() {
            ResponseEntity<Map> resp = rest.exchange(
                    "/admin/usuarios?q=admin", HttpMethod.GET,
                    new HttpEntity<>(headers(adminToken)), Map.class);

            assertEquals(HttpStatus.OK, resp.getStatusCode());
            List<?> content = (List<?>) resp.getBody().get("content");
            assertFalse(content.isEmpty());
        }

        @Test
        void createAndUpdateUsuario() {
            // Create
            String body = """
                    {"email":"testadmin@test.com","password":"password123",
                     "nombreCompleto":"Test Admin","rolCodigo":"JUGADOR"}""";

            ResponseEntity<Map> createResp = rest.exchange(
                    "/admin/usuarios", HttpMethod.POST,
                    new HttpEntity<>(body, headers(adminToken)), Map.class);

            assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
            Map created = createResp.getBody();
            assertNotNull(created);
            assertEquals("testadmin@test.com", created.get("email"));
            assertEquals("JUGADOR", created.get("rol"));

            Long userId = ((Number) created.get("id")).longValue();

            // Update
            String updateBody = """
                    {"nombreCompleto":"Test Admin Updated","rolCodigo":"MODERADOR"}""";

            ResponseEntity<Map> updateResp = rest.exchange(
                    "/admin/usuarios/" + userId, HttpMethod.PUT,
                    new HttpEntity<>(updateBody, headers(adminToken)), Map.class);

            assertEquals(HttpStatus.OK, updateResp.getStatusCode());
            assertEquals("Test Admin Updated", updateResp.getBody().get("nombreCompleto"));
            assertEquals("MODERADOR", updateResp.getBody().get("rol"));
        }

        @Test
        void toggleUsuarioActivo() {
            // Get first user
            ResponseEntity<Map> listResp = rest.exchange(
                    "/admin/usuarios?size=1", HttpMethod.GET,
                    new HttpEntity<>(headers(adminToken)), Map.class);
            List<?> content = (List<?>) listResp.getBody().get("content");
            Long userId = ((Number) ((Map<?, ?>) content.get(0)).get("id")).longValue();

            // Deactivate
            String deactivate = """
                    {"activo":false}""";
            ResponseEntity<Void> resp = rest.exchange(
                    "/admin/usuarios/" + userId + "/estado", HttpMethod.PATCH,
                    new HttpEntity<>(deactivate, headers(adminToken)), Void.class);

            assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());

            // Reactivate
            String activate = """
                    {"activo":true}""";
            rest.exchange(
                    "/admin/usuarios/" + userId + "/estado", HttpMethod.PATCH,
                    new HttpEntity<>(activate, headers(adminToken)), Void.class);
        }
    }

    // =====================================================================
    // RUBROS ABM
    // =====================================================================

    @Nested
    class Rubros {

        @Test
        void listRubrosPaginated() {
            ResponseEntity<Map> resp = rest.exchange(
                    "/admin/rubros", HttpMethod.GET,
                    new HttpEntity<>(headers(adminToken)), Map.class);

            assertEquals(HttpStatus.OK, resp.getStatusCode());
            assertNotNull(resp.getBody().get("content"));
            assertTrue((int) resp.getBody().get("totalElements") > 0);
        }

        @Test
        void crudRubro() {
            // Create
            String body = """
                    {"codigo":"TEST_RUBRO","nombre":"Rubro Test","descripcion":"Desc test"}""";
            ResponseEntity<Map> createResp = rest.exchange(
                    "/admin/rubros", HttpMethod.POST,
                    new HttpEntity<>(body, headers(adminToken)), Map.class);

            assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
            Long id = ((Number) createResp.getBody().get("id")).longValue();

            // Get
            ResponseEntity<Map> getResp = rest.exchange(
                    "/admin/rubros/" + id, HttpMethod.GET,
                    new HttpEntity<>(headers(adminToken)), Map.class);
            assertEquals("TEST_RUBRO", getResp.getBody().get("codigo"));

            // Update
            String update = """
                    {"codigo":"TEST_RUBRO","nombre":"Rubro Updated","descripcion":"Updated"}""";
            ResponseEntity<Map> updateResp = rest.exchange(
                    "/admin/rubros/" + id, HttpMethod.PUT,
                    new HttpEntity<>(update, headers(adminToken)), Map.class);
            assertEquals("Rubro Updated", updateResp.getBody().get("nombre"));

            // Deactivate
            rest.exchange("/admin/rubros/" + id + "/estado", HttpMethod.PATCH,
                    new HttpEntity<>("{\"activo\":false}", headers(adminToken)), Void.class);
        }

        @Test
        void duplicateCodigoReturns400() {
            String body = """
                    {"codigo":"RETAIL_CONV","nombre":"Duplicate","descripcion":"Dup"}""";
            ResponseEntity<String> resp = rest.exchange(
                    "/admin/rubros", HttpMethod.POST,
                    new HttpEntity<>(body, headers(adminToken)), String.class);

            // Should fail because RETAIL_CONV exists in seed data
            assertTrue(resp.getStatusCode().is4xxClientError());
        }
    }

    // =====================================================================
    // PARAMETROS MACRO + TRIMESTRES
    // =====================================================================

    @Nested
    class ParametrosMacro {

        @Test
        void listParamMacro() {
            ResponseEntity<Map> resp = rest.exchange(
                    "/admin/parametros-macro", HttpMethod.GET,
                    new HttpEntity<>(headers(adminToken)), Map.class);

            assertEquals(HttpStatus.OK, resp.getStatusCode());
            assertTrue((int) resp.getBody().get("totalElements") > 0);
        }

        @Test
        void getParamMacroDetail() {
            ResponseEntity<Map> resp = rest.exchange(
                    "/admin/parametros-macro/1", HttpMethod.GET,
                    new HttpEntity<>(headers(adminToken)), Map.class);

            assertEquals(HttpStatus.OK, resp.getStatusCode());
            assertNotNull(resp.getBody().get("nombreSet"));
            assertNotNull(resp.getBody().get("ipsPatronal"));
        }

        @Test
        void getTrimestresAndReplace() {
            // Get existing trimestres
            ResponseEntity<List> getResp = rest.exchange(
                    "/admin/parametros-macro/1/trimestres", HttpMethod.GET,
                    new HttpEntity<>(headers(adminToken)), List.class);

            assertEquals(HttpStatus.OK, getResp.getStatusCode());
            assertFalse(getResp.getBody().isEmpty());

            // Replace with new values
            String trimestres = """
                    [
                      {"trimestre":1,"inflacionTrim":0.012,"tipoCambio":7200,"tpmAnual":0.085},
                      {"trimestre":2,"inflacionTrim":0.013,"tipoCambio":7250,"tpmAnual":0.087},
                      {"trimestre":3,"inflacionTrim":0.011,"tipoCambio":7300,"tpmAnual":0.086},
                      {"trimestre":4,"inflacionTrim":0.015,"tipoCambio":7350,"tpmAnual":0.090}
                    ]""";

            ResponseEntity<List> putResp = rest.exchange(
                    "/admin/parametros-macro/1/trimestres", HttpMethod.PUT,
                    new HttpEntity<>(trimestres, headers(adminToken)), List.class);

            assertEquals(HttpStatus.OK, putResp.getStatusCode());
            assertEquals(4, putResp.getBody().size());
        }
    }

    // =====================================================================
    // PARAMETROS RUBRO + TRIMESTRES
    // =====================================================================

    @Nested
    class ParametrosRubro {

        @Test
        void listParamRubroFilterByRubro() {
            ResponseEntity<Map> resp = rest.exchange(
                    "/admin/parametros-rubro?rubroId=1", HttpMethod.GET,
                    new HttpEntity<>(headers(adminToken)), Map.class);

            assertEquals(HttpStatus.OK, resp.getStatusCode());
        }

        @Test
        void getParamRubroTrimestresAndReplace() {
            // Get first param rubro
            ResponseEntity<Map> listResp = rest.exchange(
                    "/admin/parametros-rubro?size=1", HttpMethod.GET,
                    new HttpEntity<>(headers(adminToken)), Map.class);
            List<?> content = (List<?>) listResp.getBody().get("content");
            if (content.isEmpty()) return; // skip if no data
            Long paramId = ((Number) ((Map<?, ?>) content.get(0)).get("id")).longValue();

            // Get trimestres
            ResponseEntity<List> getResp = rest.exchange(
                    "/admin/parametros-rubro/" + paramId + "/trimestres", HttpMethod.GET,
                    new HttpEntity<>(headers(adminToken)), List.class);
            assertEquals(HttpStatus.OK, getResp.getStatusCode());

            // Replace
            String trimestres = """
                    [
                      {"trimestre":1,"estacionalidad":0.95},
                      {"trimestre":2,"estacionalidad":1.00},
                      {"trimestre":3,"estacionalidad":1.05},
                      {"trimestre":4,"estacionalidad":1.18}
                    ]""";
            ResponseEntity<List> putResp = rest.exchange(
                    "/admin/parametros-rubro/" + paramId + "/trimestres", HttpMethod.PUT,
                    new HttpEntity<>(trimestres, headers(adminToken)), List.class);
            assertEquals(HttpStatus.OK, putResp.getStatusCode());
            assertEquals(4, putResp.getBody().size());
        }
    }

    // =====================================================================
    // EVENTOS CATALOGO ABM
    // =====================================================================

    @Nested
    class EventosCatalogo {

        @Test
        void listEventos() {
            ResponseEntity<Map> resp = rest.exchange(
                    "/admin/eventos", HttpMethod.GET,
                    new HttpEntity<>(headers(adminToken)), Map.class);

            assertEquals(HttpStatus.OK, resp.getStatusCode());
            assertTrue((int) resp.getBody().get("totalElements") > 0);
        }

        @Test
        void listEventosFilterBySeveridad() {
            ResponseEntity<Map> resp = rest.exchange(
                    "/admin/eventos?severidad=MODERADO", HttpMethod.GET,
                    new HttpEntity<>(headers(adminToken)), Map.class);

            assertEquals(HttpStatus.OK, resp.getStatusCode());
        }

        @Test
        void crudEvento() {
            String body = """
                    {"codigo":"EVT_TEST","nombre":"Evento Test","descripcion":"Test desc",
                     "severidad":"LEVE","tipoEfecto":"DEMANDA_TOTAL","magnitudDefault":0.05,
                     "duracionQ":1,"requiereAnuncioPrevio":false}""";

            ResponseEntity<Map> createResp = rest.exchange(
                    "/admin/eventos", HttpMethod.POST,
                    new HttpEntity<>(body, headers(adminToken)), Map.class);

            assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
            Long id = ((Number) createResp.getBody().get("id")).longValue();

            // Update
            String update = """
                    {"codigo":"EVT_TEST","nombre":"Evento Updated","descripcion":"Updated",
                     "severidad":"MODERADO","tipoEfecto":"COSTO_FIJO","magnitudDefault":0.10,
                     "duracionQ":2,"requiereAnuncioPrevio":true}""";
            ResponseEntity<Map> updateResp = rest.exchange(
                    "/admin/eventos/" + id, HttpMethod.PUT,
                    new HttpEntity<>(update, headers(adminToken)), Map.class);

            assertEquals(HttpStatus.OK, updateResp.getStatusCode());
            assertEquals("Evento Updated", updateResp.getBody().get("nombre"));
            assertEquals("MODERADO", updateResp.getBody().get("severidad"));
        }

        @Test
        void createEventoRejectsDuplicateCodigo() {
            String body = """
                    {"codigo":"EVT_DUP","nombre":"Evento Dup","descripcion":"Dup",
                     "severidad":"LEVE","tipoEfecto":"DEMANDA_TOTAL","magnitudDefault":0.05,
                     "duracionQ":1,"requiereAnuncioPrevio":false}""";

            ResponseEntity<Map> first = rest.exchange(
                    "/admin/eventos", HttpMethod.POST,
                    new HttpEntity<>(body, headers(adminToken)), Map.class);
            assertEquals(HttpStatus.CREATED, first.getStatusCode());

            ResponseEntity<Map> second = rest.exchange(
                    "/admin/eventos", HttpMethod.POST,
                    new HttpEntity<>(body, headers(adminToken)), Map.class);
            assertTrue(second.getStatusCode().is4xxClientError(),
                    "Duplicate codigo should be rejected, got " + second.getStatusCode());
        }

        @Test
        void toggleEventoActivoSoftDelete() {
            String body = """
                    {"codigo":"EVT_TOGGLE","nombre":"Evento Toggle","descripcion":"x",
                     "severidad":"LEVE","tipoEfecto":"DEMANDA_TOTAL","magnitudDefault":0.05,
                     "duracionQ":1,"requiereAnuncioPrevio":false}""";

            ResponseEntity<Map> created = rest.exchange(
                    "/admin/eventos", HttpMethod.POST,
                    new HttpEntity<>(body, headers(adminToken)), Map.class);
            assertEquals(HttpStatus.CREATED, created.getStatusCode());
            Long id = ((Number) created.getBody().get("id")).longValue();
            assertEquals(Boolean.TRUE, created.getBody().get("activo"));

            // Desactivar
            ResponseEntity<Void> off = rest.exchange(
                    "/admin/eventos/" + id + "/estado", HttpMethod.PATCH,
                    new HttpEntity<>("{\"activo\":false}", headers(adminToken)), Void.class);
            assertEquals(HttpStatus.NO_CONTENT, off.getStatusCode());

            ResponseEntity<Map> afterOff = rest.exchange(
                    "/admin/eventos/" + id, HttpMethod.GET,
                    new HttpEntity<>(headers(adminToken)), Map.class);
            assertEquals(Boolean.FALSE, afterOff.getBody().get("activo"));

            // Reactivar
            ResponseEntity<Void> on = rest.exchange(
                    "/admin/eventos/" + id + "/estado", HttpMethod.PATCH,
                    new HttpEntity<>("{\"activo\":true}", headers(adminToken)), Void.class);
            assertEquals(HttpStatus.NO_CONTENT, on.getStatusCode());

            ResponseEntity<Map> afterOn = rest.exchange(
                    "/admin/eventos/" + id, HttpMethod.GET,
                    new HttpEntity<>(headers(adminToken)), Map.class);
            assertEquals(Boolean.TRUE, afterOn.getBody().get("activo"));
        }
    }

    // =====================================================================
    // ENTIDADES ABM
    // =====================================================================

    @Nested
    class Entidades {

        @Test
        void listEntidades() {
            ResponseEntity<Map> resp = rest.exchange(
                    "/admin/entidades", HttpMethod.GET,
                    new HttpEntity<>(headers(adminToken)), Map.class);

            assertEquals(HttpStatus.OK, resp.getStatusCode());
            assertTrue((int) resp.getBody().get("totalElements") > 0);
        }

        @Test
        void crudEntidad() {
            String body = """
                    {"nombre":"Universidad Test","tipo":"UNIVERSIDAD",
                     "descripcion":"Entidad de prueba","contactoNombre":"Juan",
                     "contactoEmail":"juan@test.com"}""";

            ResponseEntity<Map> createResp = rest.exchange(
                    "/admin/entidades", HttpMethod.POST,
                    new HttpEntity<>(body, headers(adminToken)), Map.class);

            assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
            Long id = ((Number) createResp.getBody().get("id")).longValue();

            // Update
            String update = """
                    {"nombre":"Universidad Updated","tipo":"UNIVERSIDAD",
                     "contactoEmail":"updated@test.com"}""";
            ResponseEntity<Map> updateResp = rest.exchange(
                    "/admin/entidades/" + id, HttpMethod.PUT,
                    new HttpEntity<>(update, headers(adminToken)), Map.class);

            assertEquals(HttpStatus.OK, updateResp.getStatusCode());
            assertEquals("Universidad Updated", updateResp.getBody().get("nombre"));
        }
    }
}

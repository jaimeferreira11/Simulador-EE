package py.simulador.usuario;

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
import py.simulador.auth.PasswordResetTokenRepository;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que el aprovisionamiento de cuentas por MODERADOR/ADMIN dispara el
 * flujo de "definir contrasena" (token de reset) para que la cuenta sea usable:
 * - POST /usuarios (alta individual): se genera 1 token de reset para el nuevo usuario.
 * - POST /usuarios/import: cada fila creada genera token; una fila fallida NO lo hace.
 *
 * <p>El token de reset se persiste de forma sincrona dentro de la creacion; el envio
 * real del email es {@code @Async} + best-effort, por lo que aqui basta con afirmar
 * la existencia del token (mismo enfoque que valida el flujo de reset sin enviar mail).
 */
class UsuarioProvisioningEmailTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UsuarioRepository usuarioRepo;

    @Autowired
    private PasswordResetTokenRepository tokenRepo;

    private String adminToken;

    @BeforeEach
    void login() {
        adminToken = loginAs("admin@simulador.py");
    }

    private String loginAs(String email) {
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("password123");
        TokenResponse tokens = rest.postForObject("/auth/login", login, TokenResponse.class);
        assertNotNull(tokens, "Login should return tokens for " + email);
        return tokens.getAccessToken();
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String uniqueEmail() {
        return "prov" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }

    // ---------- Alta individual dispara token de set-password ----------

    @Test
    void singleCreateTriggersResetTokenForNewUser() {
        String email = uniqueEmail();
        String body = """
                {"email":"%s","password":"password123",
                 "nombre_completo":"Nuevo Usuario","rol_codigo":"JUGADOR"}""".formatted(email);

        ResponseEntity<Void> resp = rest.exchange("/usuarios", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders(adminToken)), Void.class);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());

        Long id = usuarioRepo.findByEmail(email).orElseThrow().getId();
        assertEquals(1L, tokenRepo.countByUsuarioId(id),
                "El alta individual debe generar exactamente 1 token de set-password");
    }

    // ---------- Import masivo: filas creadas generan token, la fallida no ----------

    @Test
    void bulkImportTriggersTokenPerCreatedRowAndNotForFailedRow() {
        String e1 = uniqueEmail();
        String e2 = uniqueEmail();
        String dup = "moderador@simulador.py"; // ya existe (seed) -> fila fallida
        String csv = "email,nombre_completo\n"
                + e1 + ",Fila Uno\n"
                + dup + ",Fila Duplicada\n"
                + e2 + ",Fila Dos\n";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(adminToken);
        ByteArrayResource filePart = new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "usuarios.csv";
            }
        };
        MultiValueMap<String, Object> mbody = new LinkedMultiValueMap<>();
        mbody.add("file", filePart);

        ResponseEntity<Void> resp = rest.exchange("/usuarios/import", HttpMethod.POST,
                new HttpEntity<>(mbody, headers), Void.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());

        Long id1 = usuarioRepo.findByEmail(e1).orElseThrow().getId();
        Long id2 = usuarioRepo.findByEmail(e2).orElseThrow().getId();
        Long idDup = usuarioRepo.findByEmail(dup).orElseThrow().getId();

        assertEquals(1L, tokenRepo.countByUsuarioId(id1), "fila creada -> 1 token");
        assertEquals(1L, tokenRepo.countByUsuarioId(id2), "fila creada -> 1 token");
        assertEquals(0L, tokenRepo.countByUsuarioId(idDup),
                "la fila fallida (email duplicado/seed) NO debe generar token de set-password");
    }
}

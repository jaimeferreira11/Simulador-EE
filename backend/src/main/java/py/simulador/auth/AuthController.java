package py.simulador.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import py.simulador.config.SecurityUtils;
import py.simulador.api.generated.AutenticacinApi;
import py.simulador.api.generated.model.AuthPasswordResetConfirmPostRequest;
import py.simulador.api.generated.model.AuthPasswordResetRequestPostRequest;
import py.simulador.api.generated.model.AuthRefreshPostRequest;
import py.simulador.api.generated.model.LoginRequest;
import py.simulador.api.generated.model.TokenResponse;
import py.simulador.api.generated.model.Usuario;
import py.simulador.usuario.UsuarioEntity;
import py.simulador.usuario.UsuarioRepository;

@RestController
public class AuthController implements AutenticacinApi {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final LoginRateLimiter rateLimiter;
    private final UsuarioRepository usuarioRepository;
    private final RolCache rolCache;
    private final HttpServletRequest httpRequest;

    public AuthController(AuthService authService,
                          PasswordResetService passwordResetService,
                          LoginRateLimiter rateLimiter,
                          UsuarioRepository usuarioRepository,
                          RolCache rolCache,
                          HttpServletRequest httpRequest) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.rateLimiter = rateLimiter;
        this.usuarioRepository = usuarioRepository;
        this.rolCache = rolCache;
        this.httpRequest = httpRequest;
    }

    @Override
    public ResponseEntity<TokenResponse> authLoginPost(LoginRequest loginRequest) {
        String clientIp = httpRequest.getRemoteAddr();

        if (rateLimiter.isBlocked(clientIp)) {
            throw new RateLimitExceededException();
        }

        try {
            AuthService.TokenPair tokens = authService.login(
                    loginRequest.getEmail(), loginRequest.getPassword());
            rateLimiter.resetAttempts(clientIp);
            return ResponseEntity.ok(toTokenResponse(tokens));
        } catch (AuthException e) {
            rateLimiter.recordFailedAttempt(clientIp);
            throw e;
        }
    }

    @Override
    public ResponseEntity<TokenResponse> authRefreshPost(AuthRefreshPostRequest request) {
        AuthService.TokenPair tokens = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(toTokenResponse(tokens));
    }

    @Override
    public ResponseEntity<Void> authLogoutPost() {
        Long userId = SecurityUtils.getUserId();
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Usuario> authMeGet() {
        Long userId = SecurityUtils.getUserId();
        UsuarioEntity entity = usuarioRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Usuario no encontrado"));

        Usuario usuario = new Usuario();
        usuario.setId(userId);
        usuario.setEmail(entity.getEmail());
        usuario.setNombreCompleto(entity.getNombreCompleto());
        usuario.setRol(Usuario.RolEnum.fromValue(rolCache.getCodigo(entity.getRolUsuarioId())));
        usuario.setActivo(entity.isActivo());
        if (entity.getLastLoginAt() != null) {
            usuario.lastLoginAt(entity.getLastLoginAt());
        }

        return ResponseEntity.ok(usuario);
    }

    @Override
    public ResponseEntity<Void> authPasswordResetRequestPost(
            AuthPasswordResetRequestPostRequest request) {
        // Siempre retorna 204 independientemente de si el email existe o no
        passwordResetService.requestReset(request.getEmail());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> authPasswordResetConfirmPost(
            AuthPasswordResetConfirmPostRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    private TokenResponse toTokenResponse(AuthService.TokenPair tokens) {
        TokenResponse response = new TokenResponse();
        response.setAccessToken(tokens.accessToken());
        response.setRefreshToken(tokens.refreshToken());
        return response;
    }
}

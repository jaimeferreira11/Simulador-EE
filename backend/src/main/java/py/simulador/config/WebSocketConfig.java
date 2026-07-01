package py.simulador.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import py.simulador.chat.ChatService;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.equipo.EquipoMiembroRepository;
import py.simulador.usuario.UsuarioRepository;
import py.simulador.websocket.CompetenciaWebSocketHandler;
import py.simulador.websocket.WebSocketAuthInterceptor;
import py.simulador.websocket.WebSocketSessionManager;

/**
 * Configuración WebSocket: raw WS (no STOMP) en /ws/competencias/{codigo}.
 * Auth vía query param ?token= validado en el handshake interceptor.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;
    private final ChatService chatService;
    private final EquipoMiembroRepository miembroRepo;
    private final UsuarioRepository usuarioRepo;
    private final CompetenciaRepository competenciaRepo;

    public WebSocketConfig(WebSocketSessionManager sessionManager,
                           ObjectMapper objectMapper,
                           JwtUtil jwtUtil,
                           ChatService chatService,
                           EquipoMiembroRepository miembroRepo,
                           UsuarioRepository usuarioRepo,
                           CompetenciaRepository competenciaRepo) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
        this.chatService = chatService;
        this.miembroRepo = miembroRepo;
        this.usuarioRepo = usuarioRepo;
        this.competenciaRepo = competenciaRepo;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(
                    new CompetenciaWebSocketHandler(sessionManager, objectMapper,
                            chatService, miembroRepo, usuarioRepo, competenciaRepo),
                    "/ws/competencias/*")
                .addInterceptors(new WebSocketAuthInterceptor(jwtUtil))
                .setAllowedOrigins("*");
    }
}

package py.simulador.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import py.simulador.chat.ChatMensajeEntity;
import py.simulador.chat.ChatService;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.equipo.EquipoMiembroRepository;
import py.simulador.usuario.UsuarioEntity;
import py.simulador.usuario.UsuarioRepository;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handler WebSocket para la conexión de una competencia.
 * Cada sesión se registra en el room de su competencia (por código).
 * Maneja ping/pong del cliente, chat.enviar, y limpieza al desconectarse.
 */
public class CompetenciaWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(CompetenciaWebSocketHandler.class);
    private static final String ATTR_COMPETENCIA_CODIGO = "competenciaCodigo";

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final EquipoMiembroRepository miembroRepo;
    private final UsuarioRepository usuarioRepo;
    private final CompetenciaRepository competenciaRepo;

    public CompetenciaWebSocketHandler(WebSocketSessionManager sessionManager,
                                       ObjectMapper objectMapper,
                                       ChatService chatService,
                                       EquipoMiembroRepository miembroRepo,
                                       UsuarioRepository usuarioRepo,
                                       CompetenciaRepository competenciaRepo) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
        this.chatService = chatService;
        this.miembroRepo = miembroRepo;
        this.usuarioRepo = usuarioRepo;
        this.competenciaRepo = competenciaRepo;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String codigo = (String) session.getAttributes().get(ATTR_COMPETENCIA_CODIGO);
        if (codigo == null) {
            log.warn("Sesión WS sin código de competencia, cerrando");
            try { session.close(new CloseStatus(4001, "Competencia no identificada")); } catch (Exception ignored) {}
            return;
        }
        sessionManager.register(codigo, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String tipo = node.has("tipo") ? node.get("tipo").asText() : "";

            switch (tipo) {
                case "ping" -> {
                    String pong = objectMapper.writeValueAsString(
                            java.util.Map.of("tipo", "pong",
                                    "timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
                    session.sendMessage(new TextMessage(pong));
                }
                case "chat.enviar" -> handleChatEnviar(session, node);
                // Otros mensajes del cliente se ignoran silenciosamente
            }
        } catch (Exception e) {
            log.debug("Mensaje WS no válido desde sesión {}: {}", session.getId(), e.getMessage());
        }
    }

    private void handleChatEnviar(WebSocketSession session, JsonNode node) {
        Long userId = (Long) session.getAttributes().get("userId");
        String competenciaCodigo = (String) session.getAttributes().get(ATTR_COMPETENCIA_CODIGO);

        if (userId == null || competenciaCodigo == null) {
            log.warn("chat.enviar sin userId o competenciaCodigo en sesión {}", session.getId());
            return;
        }

        if (!node.has("equipoId") || !node.has("contenido")) {
            log.debug("chat.enviar sin equipoId o contenido en sesión {}", session.getId());
            return;
        }

        Long equipoId = node.get("equipoId").asLong();
        String contenido = node.get("contenido").asText("").trim();

        if (contenido.isEmpty()) {
            return;
        }

        // Validate user is member of equipo
        if (miembroRepo.findByEquipoIdAndUsuarioId(equipoId, userId).isEmpty()) {
            log.warn("Usuario {} intentó enviar chat al equipo {} sin ser miembro", userId, equipoId);
            return;
        }

        // Look up competenciaId from codigo
        Long competenciaId = competenciaRepo.findByCodigo(competenciaCodigo)
                .map(CompetenciaEntity::getId)
                .orElse(null);

        if (competenciaId == null) {
            log.warn("chat.enviar: competencia no encontrada para código {}", competenciaCodigo);
            return;
        }

        // Get user display name
        String nombreUsuario = usuarioRepo.findById(userId)
                .map(UsuarioEntity::getNombreCompleto)
                .orElse("Desconocido");

        // Persist and broadcast
        ChatMensajeEntity msg = chatService.enviar(equipoId, competenciaId, userId, contenido);
        chatService.broadcastAndNotify(msg, competenciaCodigo, nombreUsuario);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String codigo = (String) session.getAttributes().get(ATTR_COMPETENCIA_CODIGO);
        if (codigo != null) {
            sessionManager.unregister(codigo, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("Error de transporte WS sesión {}: {}", session.getId(), exception.getMessage());
        String codigo = (String) session.getAttributes().get(ATTR_COMPETENCIA_CODIGO);
        if (codigo != null) {
            sessionManager.unregister(codigo, session);
        }
    }
}

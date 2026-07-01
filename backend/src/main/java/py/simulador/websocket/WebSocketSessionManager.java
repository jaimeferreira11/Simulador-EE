package py.simulador.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Registro thread-safe de sesiones WebSocket agrupadas por código de competencia.
 * Cada competencia activa tiene un "room" con N sesiones conectadas.
 */
@Component
public class WebSocketSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionManager.class);

    /** competenciaCodigo → sesiones activas */
    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    /** userId → sesiones activas del usuario */
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    public void register(String competenciaCodigo, WebSocketSession session) {
        rooms.computeIfAbsent(competenciaCodigo, k -> new CopyOnWriteArraySet<>()).add(session);

        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        }

        log.info("WS conectado: sesión={} competencia={} usuario={} (total={})",
                session.getId(), competenciaCodigo, userId, rooms.get(competenciaCodigo).size());
    }

    public void unregister(String competenciaCodigo, WebSocketSession session) {
        Set<WebSocketSession> room = rooms.get(competenciaCodigo);
        if (room != null) {
            room.remove(session);
            if (room.isEmpty()) {
                rooms.remove(competenciaCodigo);
            }
            log.info("WS desconectado: sesión={} competencia={}", session.getId(), competenciaCodigo);
        }

        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            Set<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }
    }

    /** Envía mensaje de texto a todas las sesiones de una competencia */
    public void broadcast(String competenciaCodigo, String json) {
        Set<WebSocketSession> room = rooms.get(competenciaCodigo);
        if (room == null || room.isEmpty()) return;

        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : room) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    log.warn("Error enviando WS a sesión {}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }

    /** Envía mensaje solo a sesiones de un equipo específico dentro de la competencia */
    public void sendToEquipo(String competenciaCodigo, Long equipoId, String json) {
        Set<WebSocketSession> room = rooms.get(competenciaCodigo);
        if (room == null || room.isEmpty()) return;

        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : room) {
            if (session.isOpen() && equipoId.equals(session.getAttributes().get("equipoId"))) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    log.warn("Error enviando WS a sesión {}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }

    /** Envía mensaje a todas las sesiones de un usuario específico */
    public void sendToUser(Long userId, String json) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) return;

        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    log.warn("Error enviando WS a sesión {} del usuario {}: {}",
                            session.getId(), userId, e.getMessage());
                }
            }
        }
    }

    /** Returns true if the user has at least one active WS session */
    public boolean isUserOnline(Long userId) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }
}

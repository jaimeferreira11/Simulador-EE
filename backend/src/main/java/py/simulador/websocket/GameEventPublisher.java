package py.simulador.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Publica eventos de juego a los clientes WebSocket conectados.
 * Los controllers llaman a publish() después de que la transacción se confirma.
 * Resuelve competenciaId → codigo internamente.
 */
@Component
public class GameEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(GameEventPublisher.class);

    private final WebSocketSessionManager sessionManager;
    private final CompetenciaRepository competenciaRepo;
    private final ObjectMapper objectMapper;

    public GameEventPublisher(WebSocketSessionManager sessionManager,
                              CompetenciaRepository competenciaRepo,
                              ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.competenciaRepo = competenciaRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Broadcast a todos los participantes de la competencia.
     *
     * @param competenciaId ID numérico de la competencia
     * @param tipo          nombre del evento (ej. "trimestre.procesado")
     * @param payload       datos específicos del evento
     */
    public void publish(Long competenciaId, String tipo, Map<String, Object> payload) {
        String codigo = resolverCodigo(competenciaId);
        if (codigo == null) return;

        String json = serialize(competenciaId, tipo, payload);
        if (json != null) {
            sessionManager.broadcast(codigo, json);
            log.debug("WS evento '{}' publicado a competencia '{}' (id={})", tipo, codigo, competenciaId);
        }
    }

    /** Envío dirigido a un equipo específico (ej. decision.recibida) */
    public void publishToEquipo(Long competenciaId, Long equipoId, String tipo, Map<String, Object> payload) {
        String codigo = resolverCodigo(competenciaId);
        if (codigo == null) return;

        String json = serialize(competenciaId, tipo, payload);
        if (json != null) {
            sessionManager.sendToEquipo(codigo, equipoId, json);
        }
    }

    /**
     * Envío dirigido a un usuario específico (ej. notificacion.nueva).
     * No requiere competenciaId — el mensaje se envía a todas las sesiones del usuario.
     */
    public void sendToUser(Long usuarioId, String tipo, Map<String, Object> payload) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("tipo", tipo);
        message.put("timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        message.put("payload", payload);
        try {
            String json = objectMapper.writeValueAsString(message);
            sessionManager.sendToUser(usuarioId, json);
            log.debug("WS evento '{}' enviado a usuario {}", tipo, usuarioId);
        } catch (JsonProcessingException e) {
            log.error("Error serializando evento WS '{}' para usuario {}: {}", tipo, usuarioId, e.getMessage());
        }
    }

    private String resolverCodigo(Long competenciaId) {
        Optional<CompetenciaEntity> comp = competenciaRepo.findById(competenciaId);
        if (comp.isEmpty()) {
            log.warn("WS publish: competencia {} no encontrada", competenciaId);
            return null;
        }
        return comp.get().getCodigo();
    }

    private String serialize(Long competenciaId, String tipo, Map<String, Object> payload) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("tipo", tipo);
        message.put("timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        message.put("competencia_id", competenciaId);
        message.put("payload", payload);
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Error serializando evento WS '{}': {}", tipo, e.getMessage());
            return null;
        }
    }
}

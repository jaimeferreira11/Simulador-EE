package py.simulador.websocket;

import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import py.simulador.config.JwtUtil;

import java.net.URI;
import java.util.Map;

/**
 * Interceptor del handshake WebSocket.
 * Valida JWT (pasado como query param ?token=) y extrae el código de competencia del path.
 * Si el token es inválido, rechaza la conexión (el handler cierra con 4001).
 */
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtUtil jwtUtil;

    public WebSocketAuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // Extraer código de competencia del path: /ws/competencias/{codigo}
        URI uri = request.getURI();
        String path = uri.getPath();
        String codigo = extractCodigo(path);
        if (codigo == null || codigo.isBlank()) {
            log.warn("WS handshake rechazado: código de competencia no encontrado en path {}", path);
            return false;
        }
        attributes.put("competenciaCodigo", codigo);

        // Extraer token del query param
        String token = extractToken(uri);
        if (token == null || !jwtUtil.isValid(token)) {
            log.warn("WS handshake rechazado: token inválido o ausente para competencia {}", codigo);
            return false;
        }

        // Guardar datos del usuario en la sesión
        Claims claims = jwtUtil.parseToken(token);
        attributes.put("userId", Long.valueOf(claims.getSubject()));
        attributes.put("rol", claims.get("rol", String.class));

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No-op
    }

    /** Extrae el código de competencia del path /ws/competencias/{codigo} */
    private String extractCodigo(String path) {
        String prefix = "/ws/competencias/";
        int idx = path.indexOf(prefix);
        if (idx < 0) return null;
        String remainder = path.substring(idx + prefix.length());
        // Remover trailing slash si existe
        int slash = remainder.indexOf('/');
        return slash > 0 ? remainder.substring(0, slash) : remainder;
    }

    /** Extrae el token del query param ?token=xxx */
    private String extractToken(URI uri) {
        String query = uri.getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return null;
    }
}

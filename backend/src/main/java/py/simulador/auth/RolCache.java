package py.simulador.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RolCache {

    private final JdbcTemplate jdbc;
    private final Map<Long, String> idToCodigo = new ConcurrentHashMap<>();
    private final Map<String, Long> codigoToId = new ConcurrentHashMap<>();

    public RolCache(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void load() {
        jdbc.query("SELECT id, codigo FROM sim.rol_usuario", rs -> {
            Long id = rs.getLong("id");
            String codigo = rs.getString("codigo");
            idToCodigo.put(id, codigo);
            codigoToId.put(codigo, id);
        });
    }

    public String getCodigo(Long rolId) {
        return idToCodigo.getOrDefault(rolId, "UNKNOWN");
    }

    public Long getId(String codigo) {
        return codigoToId.get(codigo);
    }
}

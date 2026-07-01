package py.simulador.notificacion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.equipo.EquipoMiembroEntity;
import py.simulador.equipo.EquipoMiembroRepository;
import py.simulador.equipo.EquipoRepository;
import py.simulador.websocket.GameEventPublisher;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificacionService {

    private final NotificacionRepository repository;
    private final EquipoRepository equipoRepo;
    private final EquipoMiembroRepository miembroRepo;
    private final GameEventPublisher eventPublisher;

    public NotificacionService(NotificacionRepository repository,
                               EquipoRepository equipoRepo,
                               EquipoMiembroRepository miembroRepo,
                               GameEventPublisher eventPublisher) {
        this.repository = repository;
        this.equipoRepo = equipoRepo;
        this.miembroRepo = miembroRepo;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Crea notificaciones para todos los jugadores de una competencia.
     */
    @Transactional
    public void notificarCompetencia(Long competenciaId, String tipo,
                                      String titulo, String descripcion,
                                      String severidad) {
        List<Long> jugadorIds = obtenerJugadoresDeCompetencia(competenciaId);
        for (Long uid : jugadorIds) {
            crearNotificacion(uid, competenciaId, tipo, titulo, descripcion, severidad);
        }
    }

    /**
     * Crea notificacion para un usuario especifico.
     */
    @Transactional
    public void notificarUsuario(Long usuarioId, Long competenciaId,
                                  String tipo, String titulo, String descripcion,
                                  String severidad) {
        crearNotificacion(usuarioId, competenciaId, tipo, titulo, descripcion, severidad);
    }

    @Transactional(readOnly = true)
    public List<NotificacionEntity> listar(Long usuarioId, Boolean leida, int page, int size) {
        int offset = page * size;
        if (leida != null) {
            return repository.findByUsuarioIdAndLeida(usuarioId, leida, size, offset);
        }
        return repository.findByUsuarioId(usuarioId, size, offset);
    }

    @Transactional(readOnly = true)
    public long contar(Long usuarioId, Boolean leida) {
        if (leida != null) {
            return repository.countByUsuarioIdAndLeida(usuarioId, leida);
        }
        return repository.countByUsuarioId(usuarioId);
    }

    @Transactional(readOnly = true)
    public long contarTotal(Long usuarioId, Boolean leida) {
        if (leida != null) {
            return repository.countByUsuarioIdAndLeida(usuarioId, leida);
        }
        return repository.countByUsuarioId(usuarioId);
    }

    @Transactional
    public void marcarLeida(Long notificacionId, Long usuarioId) {
        repository.marcarLeida(notificacionId, usuarioId);
    }

    @Transactional
    public void marcarTodasLeidas(Long usuarioId) {
        repository.marcarTodasLeidas(usuarioId);
    }

    private void crearNotificacion(Long usuarioId, Long competenciaId,
                                    String tipo, String titulo, String descripcion,
                                    String severidad) {
        NotificacionEntity n = new NotificacionEntity();
        n.setUsuarioId(usuarioId);
        n.setCompetenciaId(competenciaId);
        n.setTipo(tipo);
        n.setTitulo(titulo);
        n.setDescripcion(descripcion);
        n.setSeveridad(severidad);
        n.setLeida(false);
        n.setCreatedAt(OffsetDateTime.now());
        repository.save(n);

        // Emit WebSocket notification to the user
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", n.getId());
        payload.put("tipo_notificacion", tipo);
        payload.put("titulo", titulo);
        payload.put("descripcion", descripcion);
        payload.put("severidad", severidad);
        payload.put("competencia_id", competenciaId);
        payload.put("created_at", n.getCreatedAt());
        eventPublisher.sendToUser(usuarioId, "notificacion.nueva", payload);
    }

    private List<Long> obtenerJugadoresDeCompetencia(Long competenciaId) {
        return equipoRepo.findByCompetenciaId(competenciaId).stream()
                .flatMap(equipo -> miembroRepo.findByEquipoId(equipo.getId()).stream())
                .map(EquipoMiembroEntity::getUsuarioId)
                .distinct()
                .toList();
    }
}

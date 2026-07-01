package py.simulador.notificacion;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface NotificacionRepository extends CrudRepository<NotificacionEntity, Long> {

    @Query("SELECT * FROM sim.notificacion WHERE usuario_id = :usuarioId ORDER BY created_at DESC LIMIT :size OFFSET :offset")
    List<NotificacionEntity> findByUsuarioId(Long usuarioId, int size, int offset);

    @Query("SELECT * FROM sim.notificacion WHERE usuario_id = :usuarioId AND leida = :leida ORDER BY created_at DESC LIMIT :size OFFSET :offset")
    List<NotificacionEntity> findByUsuarioIdAndLeida(Long usuarioId, boolean leida, int size, int offset);

    @Query("SELECT COUNT(*) FROM sim.notificacion WHERE usuario_id = :usuarioId")
    long countByUsuarioId(Long usuarioId);

    @Query("SELECT COUNT(*) FROM sim.notificacion WHERE usuario_id = :usuarioId AND leida = :leida")
    long countByUsuarioIdAndLeida(Long usuarioId, boolean leida);

    @Modifying
    @Query("UPDATE sim.notificacion SET leida = true WHERE id = :id AND usuario_id = :usuarioId")
    void marcarLeida(Long id, Long usuarioId);

    @Modifying
    @Query("UPDATE sim.notificacion SET leida = true WHERE usuario_id = :usuarioId AND leida = false")
    void marcarTodasLeidas(Long usuarioId);
}

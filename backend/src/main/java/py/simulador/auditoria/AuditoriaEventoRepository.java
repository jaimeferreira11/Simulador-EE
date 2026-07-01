package py.simulador.auditoria;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface AuditoriaEventoRepository extends CrudRepository<AuditoriaEventoEntity, Long> {

    @Query("SELECT * FROM sim.auditoria_evento WHERE competencia_id = :competenciaId ORDER BY ocurrido_at DESC")
    List<AuditoriaEventoEntity> findByCompetenciaId(Long competenciaId);

    @Query("SELECT * FROM sim.auditoria_evento WHERE competencia_id = :competenciaId AND tipo_accion = :tipoAccion ORDER BY ocurrido_at DESC")
    List<AuditoriaEventoEntity> findByCompetenciaIdAndTipoAccion(Long competenciaId, String tipoAccion);

    @Modifying
    @Query("INSERT INTO sim.auditoria_evento (competencia_id, usuario_id, tipo_accion, descripcion, ocurrido_at) VALUES (:competenciaId, :usuarioId, :tipoAccion, :descripcion, :ocurridoAt)")
    void insertar(Long competenciaId, Long usuarioId, String tipoAccion, String descripcion, OffsetDateTime ocurridoAt);
}

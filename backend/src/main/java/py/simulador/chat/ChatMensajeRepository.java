package py.simulador.chat;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ChatMensajeRepository extends CrudRepository<ChatMensajeEntity, Long> {

    @Query("""
        SELECT * FROM sim.chat_mensaje
        WHERE equipo_id = :equipoId AND competencia_id = :competenciaId
        ORDER BY created_at DESC
        LIMIT :size OFFSET :offset
        """)
    List<ChatMensajeEntity> findByEquipoAndCompetencia(Long equipoId, Long competenciaId, int size, int offset);

    @Query("""
        SELECT COUNT(*) FROM sim.chat_mensaje
        WHERE equipo_id = :equipoId AND competencia_id = :competenciaId
        """)
    long countByEquipoAndCompetencia(Long equipoId, Long competenciaId);

    @Modifying
    @Query("""
        DELETE FROM sim.chat_mensaje
        WHERE id IN (
            SELECT id FROM sim.chat_mensaje
            WHERE equipo_id = :equipoId AND competencia_id = :competenciaId
            ORDER BY created_at ASC
            LIMIT GREATEST(0,
                (SELECT COUNT(*) FROM sim.chat_mensaje
                 WHERE equipo_id = :equipoId AND competencia_id = :competenciaId) - 150
            )
        )
        """)
    void trimOldMessages(Long equipoId, Long competenciaId);
}

package py.simulador.evento;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface EventoCompetenciaRepository extends CrudRepository<EventoCompetenciaEntity, Long> {

    @Query("SELECT * FROM sim.evento_competencia WHERE competencia_id = :competenciaId ORDER BY created_at")
    List<EventoCompetenciaEntity> findByCompetenciaId(Long competenciaId);

    @Query("SELECT * FROM sim.evento_competencia WHERE trimestre_id = :trimestreId ORDER BY created_at")
    List<EventoCompetenciaEntity> findByTrimestreId(Long trimestreId);

    @Query("SELECT * FROM sim.evento_competencia WHERE competencia_id = :competenciaId AND trimestre_id = :trimestreId")
    List<EventoCompetenciaEntity> findByCompetenciaIdAndTrimestreId(Long competenciaId, Long trimestreId);

    /**
     * Eventos activos para un trimestre: incluye eventos asignados directamente a este Q
     * Y eventos de trimestres anteriores cuya duración cubre este Q.
     * Ej: evento en Q2 con duracion=2 se encuentra al procesar Q2 y Q3.
     */
    @Query("""
        SELECT ec.* FROM sim.evento_competencia ec
        JOIN sim.trimestre t_origen ON ec.trimestre_id = t_origen.id
        JOIN sim.trimestre t_actual ON t_actual.id = :trimestreId
        WHERE ec.competencia_id = :competenciaId
          AND t_origen.numero <= t_actual.numero
          AND t_origen.numero + ec.duracion_aplicada > t_actual.numero
        ORDER BY ec.created_at
        """)
    List<EventoCompetenciaEntity> findActivosParaTrimestre(Long competenciaId, Long trimestreId);
}

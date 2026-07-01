package py.simulador.competencia;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface CompetenciaRepository extends CrudRepository<CompetenciaEntity, Long> {

    @Query("SELECT * FROM sim.competencia WHERE codigo = :codigo")
    Optional<CompetenciaEntity> findByCodigo(String codigo);

    @Query("SELECT * FROM sim.competencia WHERE moderador_id = :moderadorId ORDER BY created_at DESC")
    List<CompetenciaEntity> findByModeradorId(Long moderadorId);

    @Query("SELECT * FROM sim.competencia WHERE estado = :estado ORDER BY created_at DESC")
    List<CompetenciaEntity> findByEstado(String estado);

    @Query("SELECT * FROM sim.competencia WHERE moderador_id = :moderadorId AND estado = :estado ORDER BY created_at DESC")
    List<CompetenciaEntity> findByModeradorIdAndEstado(Long moderadorId, String estado);

    @Query("SELECT DISTINCT c.* FROM sim.competencia c JOIN sim.equipo e ON e.competencia_id = c.id JOIN sim.equipo_miembro m ON m.equipo_id = e.id WHERE m.usuario_id = :usuarioId ORDER BY c.created_at DESC")
    List<CompetenciaEntity> findByMiembroUsuarioId(Long usuarioId);

    @Query("SELECT COUNT(*) FROM sim.competencia WHERE estado = :estado")
    long countByEstado(String estado);

    // --- Filtered queries for moderador with entidad, estado, anio ---

    @Query("SELECT * FROM sim.competencia WHERE moderador_id = :moderadorId"
            + " AND (:entidadId IS NULL OR entidad_id = :entidadId)"
            + " AND (:estado IS NULL OR estado = :estado)"
            + " AND (:anio IS NULL OR EXTRACT(YEAR FROM created_at) = :anio)"
            + " ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    List<CompetenciaEntity> findByModeradorFiltered(Long moderadorId, Long entidadId,
                                                     String estado, Integer anio,
                                                     int limit, int offset);

    @Query("SELECT COUNT(*) FROM sim.competencia WHERE moderador_id = :moderadorId"
            + " AND (:entidadId IS NULL OR entidad_id = :entidadId)"
            + " AND (:estado IS NULL OR estado = :estado)"
            + " AND (:anio IS NULL OR EXTRACT(YEAR FROM created_at) = :anio)")
    long countByModeradorFiltered(Long moderadorId, Long entidadId,
                                   String estado, Integer anio);
}

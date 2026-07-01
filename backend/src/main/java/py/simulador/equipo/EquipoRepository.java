package py.simulador.equipo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface EquipoRepository extends CrudRepository<EquipoEntity, Long> {

    @Query("SELECT * FROM sim.equipo WHERE competencia_id = :competenciaId ORDER BY nombre_empresa")
    List<EquipoEntity> findByCompetenciaId(Long competenciaId);

    @Query("SELECT COUNT(*) FROM sim.equipo WHERE competencia_id = :competenciaId")
    long countByCompetenciaId(Long competenciaId);

    @Query("SELECT * FROM sim.equipo WHERE competencia_id = :competenciaId AND tipo = :tipo ORDER BY id")
    List<EquipoEntity> findByCompetenciaIdAndTipo(Long competenciaId, String tipo);
}

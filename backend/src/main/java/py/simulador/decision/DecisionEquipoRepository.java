package py.simulador.decision;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface DecisionEquipoRepository extends CrudRepository<DecisionEquipoEntity, Long> {

    @Query("SELECT * FROM sim.decision_equipo WHERE equipo_id = :equipoId AND trimestre_id = :trimestreId")
    Optional<DecisionEquipoEntity> findByEquipoIdAndTrimestreId(Long equipoId, Long trimestreId);

    @Query("SELECT * FROM sim.decision_equipo WHERE trimestre_id = :trimestreId ORDER BY equipo_id")
    List<DecisionEquipoEntity> findByTrimestreId(Long trimestreId);

    @Query("SELECT * FROM sim.decision_equipo WHERE trimestre_id = :trimestreId AND estado = :estado")
    List<DecisionEquipoEntity> findByTrimestreIdAndEstado(Long trimestreId, String estado);
}

package py.simulador.decision;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface DecisionCampoLogRepository extends CrudRepository<DecisionCampoLogEntity, Long> {

    @Query("SELECT * FROM sim.decision_campo_log WHERE decision_equipo_id = :decisionEquipoId ORDER BY modificado_at")
    List<DecisionCampoLogEntity> findByDecisionEquipoId(Long decisionEquipoId);

    @Query("SELECT * FROM sim.decision_campo_log WHERE decision_equipo_id = :decisionEquipoId AND campo = :campo ORDER BY modificado_at")
    List<DecisionCampoLogEntity> findByDecisionEquipoIdAndCampo(Long decisionEquipoId, String campo);
}

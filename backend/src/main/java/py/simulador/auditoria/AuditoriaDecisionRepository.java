package py.simulador.auditoria;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface AuditoriaDecisionRepository extends CrudRepository<AuditoriaDecisionEntity, Long> {

    @Query("SELECT * FROM sim.auditoria_decision WHERE decision_equipo_id = :decisionEquipoId ORDER BY ocurrido_at")
    List<AuditoriaDecisionEntity> findByDecisionEquipoId(Long decisionEquipoId);
}

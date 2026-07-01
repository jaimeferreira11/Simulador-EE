package py.simulador.escenario;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface EscenarioEventoRepository extends CrudRepository<EscenarioEventoEntity, Long> {

    @Query("SELECT * FROM sim.escenario_evento WHERE escenario_id = :escenarioId ORDER BY trimestre_numero")
    List<EscenarioEventoEntity> findByEscenarioId(Long escenarioId);
}

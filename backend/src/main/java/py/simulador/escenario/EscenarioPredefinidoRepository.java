package py.simulador.escenario;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface EscenarioPredefinidoRepository extends CrudRepository<EscenarioPredefinidoEntity, Long> {

    @Query("SELECT * FROM sim.escenario_predefinido WHERE activo = true ORDER BY nombre")
    List<EscenarioPredefinidoEntity> findAllActivos();

    @Query("SELECT * FROM sim.escenario_predefinido WHERE activo = true AND rubro_id = :rubroId ORDER BY nombre")
    List<EscenarioPredefinidoEntity> findAllActivosByRubro(Long rubroId);
}

package py.simulador.catalogo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface AreaDecisionRepository extends CrudRepository<AreaDecisionEntity, Long> {

    @Query("SELECT * FROM sim.area_decision WHERE activo = TRUE ORDER BY orden")
    List<AreaDecisionEntity> findAllActivas();

    @Query("SELECT * FROM sim.area_decision WHERE codigo = :codigo")
    Optional<AreaDecisionEntity> findByCodigo(String codigo);
}

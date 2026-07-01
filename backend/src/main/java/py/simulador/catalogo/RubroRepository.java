package py.simulador.catalogo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface RubroRepository extends CrudRepository<RubroEntity, Long> {

    @Query("SELECT * FROM sim.rubro WHERE codigo = :codigo")
    Optional<RubroEntity> findByCodigo(String codigo);

    @Query("SELECT * FROM sim.rubro WHERE activo = true ORDER BY nombre")
    List<RubroEntity> findAllActivos();
}

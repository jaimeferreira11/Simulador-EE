package py.simulador.catalogo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ParametroRubroRepository extends CrudRepository<ParametroRubroEntity, Long> {

    @Query("SELECT * FROM sim.parametro_rubro WHERE rubro_id = :rubroId AND activo = true")
    List<ParametroRubroEntity> findByRubroIdActivos(Long rubroId);
}

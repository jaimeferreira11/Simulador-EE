package py.simulador.catalogo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ParametroRubroTrimestreRepository extends CrudRepository<ParametroRubroTrimestreEntity, Long> {

    @Query("SELECT * FROM sim.parametro_rubro_trimestre WHERE rubro_param_id = :rubroParamId ORDER BY trimestre")
    List<ParametroRubroTrimestreEntity> findByRubroParamId(Long rubroParamId);
}

package py.simulador.catalogo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ParametroMacroTrimestreRepository extends CrudRepository<ParametroMacroTrimestreEntity, Long> {

    @Query("SELECT * FROM sim.parametro_macro_trimestre WHERE macro_id = :macroId ORDER BY trimestre")
    List<ParametroMacroTrimestreEntity> findByMacroId(Long macroId);
}

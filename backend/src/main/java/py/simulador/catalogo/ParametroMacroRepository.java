package py.simulador.catalogo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ParametroMacroRepository extends CrudRepository<ParametroMacroEntity, Long> {

    @Query("SELECT * FROM sim.parametro_macro WHERE activo = true ORDER BY vigente_desde DESC")
    List<ParametroMacroEntity> findAllActivos();
}

package py.simulador.catalogo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface MateriaPrimaRubroRepository extends CrudRepository<MateriaPrimaRubroEntity, Long> {

    @Query("SELECT * FROM sim.materia_prima_rubro WHERE rubro_id = :rubroId ORDER BY orden ASC")
    List<MateriaPrimaRubroEntity> findByRubroIdOrderByOrdenAsc(Long rubroId);
}

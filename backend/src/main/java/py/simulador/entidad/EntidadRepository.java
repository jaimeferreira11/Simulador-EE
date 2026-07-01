package py.simulador.entidad;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface EntidadRepository extends CrudRepository<EntidadEntity, Long> {

    @Query("SELECT * FROM sim.entidad WHERE activa = TRUE ORDER BY nombre")
    List<EntidadEntity> findAllActivas();

    @Query("SELECT * FROM sim.entidad ORDER BY nombre")
    List<EntidadEntity> findAllOrdered();
}

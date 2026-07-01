package py.simulador.catalogo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface EventoCatalogoRepository extends CrudRepository<EventoCatalogoEntity, Long> {

    @Query("SELECT * FROM sim.evento_catalogo WHERE activo = true ORDER BY nombre")
    List<EventoCatalogoEntity> findAllActivos();

    @Query("SELECT * FROM sim.evento_catalogo WHERE activo = true AND (rubro_id IS NULL OR rubro_id = :rubroId) ORDER BY nombre")
    List<EventoCatalogoEntity> findActivosByRubro(Long rubroId);

    @Query("SELECT * FROM sim.evento_catalogo WHERE codigo = :codigo")
    Optional<EventoCatalogoEntity> findByCodigo(String codigo);
}

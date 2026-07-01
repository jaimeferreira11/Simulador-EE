package py.simulador.resultado;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface ResultadoCalculoRepository extends CrudRepository<ResultadoCalculoEntity, Long> {

    @Query("SELECT * FROM sim.resultado_calculo WHERE equipo_id = :equipoId AND trimestre_id = :trimestreId")
    Optional<ResultadoCalculoEntity> findByEquipoIdAndTrimestreId(Long equipoId, Long trimestreId);

    @Query("SELECT * FROM sim.resultado_calculo WHERE trimestre_id = :trimestreId ORDER BY equipo_id")
    List<ResultadoCalculoEntity> findByTrimestreId(Long trimestreId);
}

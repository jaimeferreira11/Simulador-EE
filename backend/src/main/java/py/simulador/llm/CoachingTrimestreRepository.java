package py.simulador.llm;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import java.util.List;
import java.util.Optional;

public interface CoachingTrimestreRepository extends CrudRepository<CoachingTrimestreEntity, Long> {

    @Query("SELECT * FROM sim.coaching_trimestre WHERE trimestre_id = :trimestreId AND equipo_id = :equipoId")
    Optional<CoachingTrimestreEntity> findByTrimestreIdAndEquipoId(Long trimestreId, Long equipoId);

    @Query("SELECT * FROM sim.coaching_trimestre WHERE trimestre_id = :trimestreId")
    List<CoachingTrimestreEntity> findByTrimestreId(Long trimestreId);
}

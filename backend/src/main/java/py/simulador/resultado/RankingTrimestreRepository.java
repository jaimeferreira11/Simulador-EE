package py.simulador.resultado;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface RankingTrimestreRepository extends CrudRepository<RankingTrimestreEntity, Long> {

    @Query("SELECT * FROM sim.ranking_trimestre WHERE competencia_id = :competenciaId AND trimestre_id = :trimestreId ORDER BY posicion")
    List<RankingTrimestreEntity> findByCompetenciaIdAndTrimestreId(Long competenciaId, Long trimestreId);

    @Query("SELECT * FROM sim.ranking_trimestre WHERE competencia_id = :competenciaId ORDER BY trimestre_id, posicion")
    List<RankingTrimestreEntity> findByCompetenciaId(Long competenciaId);
}

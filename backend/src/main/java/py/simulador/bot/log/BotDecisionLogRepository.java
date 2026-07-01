package py.simulador.bot.log;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface BotDecisionLogRepository extends CrudRepository<BotDecisionLogEntity, Long> {

    @Query("SELECT * FROM sim.bot_decision_log " +
           "WHERE equipo_id = :equipoId AND trimestre_id = :trimestreId " +
           "ORDER BY id DESC")
    List<BotDecisionLogEntity> findByEquipoIdAndTrimestreId(Long equipoId, Long trimestreId);

    @Query("SELECT * FROM sim.bot_decision_log WHERE trimestre_id = :trimestreId ORDER BY id")
    List<BotDecisionLogEntity> findByTrimestreId(Long trimestreId);
}

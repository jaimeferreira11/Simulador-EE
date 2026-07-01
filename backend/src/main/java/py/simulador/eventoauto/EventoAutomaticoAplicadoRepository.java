package py.simulador.eventoauto;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface EventoAutomaticoAplicadoRepository extends CrudRepository<EventoAutomaticoAplicadoEntity, Long> {

    @Query("SELECT * FROM sim.evento_automatico_aplicado WHERE competencia_id = :competenciaId ORDER BY trimestre_origen, equipo_id")
    List<EventoAutomaticoAplicadoEntity> findByCompetenciaId(Long competenciaId);

    @Query("SELECT * FROM sim.evento_automatico_aplicado WHERE competencia_id = :competenciaId AND equipo_id = :equipoId AND trimestre_efecto_inicio <= :trimestreActual AND trimestre_efecto_fin >= :trimestreActual")
    List<EventoAutomaticoAplicadoEntity> findActivosParaEquipo(Long competenciaId, Long equipoId, int trimestreActual);
}

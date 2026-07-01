package py.simulador.eventoauto;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface EventoAutomaticoReglaRepository extends CrudRepository<EventoAutomaticoReglaEntity, Long> {

    @Query("SELECT * FROM sim.evento_automatico_regla WHERE activo = true ORDER BY nombre")
    List<EventoAutomaticoReglaEntity> findAllActivos();
}

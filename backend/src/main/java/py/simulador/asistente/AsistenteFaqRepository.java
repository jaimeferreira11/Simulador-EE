package py.simulador.asistente;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface AsistenteFaqRepository extends CrudRepository<AsistenteFaqEntity, Long> {
    List<AsistenteFaqEntity> findByActivaTrueOrderByOrdenAsc();
}

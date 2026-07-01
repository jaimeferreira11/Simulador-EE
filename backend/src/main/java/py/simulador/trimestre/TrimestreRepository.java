package py.simulador.trimestre;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface TrimestreRepository extends CrudRepository<TrimestreEntity, Long> {

    @Query("SELECT * FROM sim.trimestre WHERE competencia_id = :competenciaId ORDER BY numero")
    List<TrimestreEntity> findByCompetenciaId(Long competenciaId);

    @Query("SELECT * FROM sim.trimestre WHERE competencia_id = :competenciaId AND numero = :numero")
    Optional<TrimestreEntity> findByCompetenciaIdAndNumero(Long competenciaId, short numero);

    @Query("SELECT * FROM sim.trimestre WHERE competencia_id = :competenciaId AND estado = :estado")
    List<TrimestreEntity> findByCompetenciaIdAndEstado(Long competenciaId, String estado);

    @Query("SELECT * FROM sim.trimestre WHERE competencia_id = :competenciaId AND estado = 'PROCESADO' ORDER BY numero DESC LIMIT 1")
    Optional<TrimestreEntity> findUltimoTrimestreProcesado(Long competenciaId);
}

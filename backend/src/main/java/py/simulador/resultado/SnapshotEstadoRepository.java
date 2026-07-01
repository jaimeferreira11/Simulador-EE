package py.simulador.resultado;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface SnapshotEstadoRepository extends CrudRepository<SnapshotEstadoEntity, Long> {

    @Query("SELECT * FROM sim.snapshot_estado WHERE equipo_id = :equipoId AND trimestre_id = :trimestreId AND momento = :momento")
    Optional<SnapshotEstadoEntity> findByEquipoIdAndTrimestreIdAndMomento(Long equipoId, Long trimestreId, String momento);

    @Query("SELECT * FROM sim.snapshot_estado WHERE equipo_id = :equipoId ORDER BY trimestre_id, momento")
    List<SnapshotEstadoEntity> findByEquipoId(Long equipoId);

    @Query("SELECT * FROM sim.snapshot_estado WHERE trimestre_id = :trimestreId AND momento = :momento ORDER BY equipo_id")
    List<SnapshotEstadoEntity> findByTrimestreIdAndMomento(Long trimestreId, String momento);
}

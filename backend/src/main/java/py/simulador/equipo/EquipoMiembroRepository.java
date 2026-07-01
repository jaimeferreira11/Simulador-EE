package py.simulador.equipo;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface EquipoMiembroRepository extends CrudRepository<EquipoMiembroEntity, Long> {

    @Query("SELECT * FROM sim.equipo_miembro WHERE equipo_id = :equipoId")
    List<EquipoMiembroEntity> findByEquipoId(Long equipoId);

    @Query("SELECT * FROM sim.equipo_miembro WHERE usuario_id = :usuarioId")
    List<EquipoMiembroEntity> findByUsuarioId(Long usuarioId);

    @Query("SELECT * FROM sim.equipo_miembro WHERE equipo_id = :equipoId AND usuario_id = :usuarioId")
    Optional<EquipoMiembroEntity> findByEquipoIdAndUsuarioId(Long equipoId, Long usuarioId);
}

package py.simulador.invitacion;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface InvitacionRepository extends CrudRepository<InvitacionEntity, Long> {

    @Query("SELECT * FROM sim.invitacion WHERE token = :token")
    Optional<InvitacionEntity> findByToken(String token);

    @Query("SELECT * FROM sim.invitacion WHERE equipo_id = :equipoId ORDER BY created_at DESC")
    List<InvitacionEntity> findByEquipoId(Long equipoId);

    @Query("SELECT * FROM sim.invitacion WHERE email = :email AND estado = 'PENDIENTE'")
    List<InvitacionEntity> findPendientesByEmail(String email);

    @Query("SELECT * FROM sim.invitacion WHERE equipo_id = :equipoId AND email = :email AND estado = 'PENDIENTE'")
    Optional<InvitacionEntity> findPendienteByEquipoIdAndEmail(Long equipoId, String email);

    @Query("SELECT COUNT(*) FROM sim.invitacion WHERE equipo_id = :equipoId AND estado = 'PENDIENTE'")
    long countPendientesByEquipoId(Long equipoId);
}

package py.simulador.auth;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends CrudRepository<PasswordResetToken, Long> {

    @Query("SELECT * FROM sim.password_reset_token WHERE token = :token AND used = FALSE")
    Optional<PasswordResetToken> findByTokenAndNotUsed(String token);

    @Query("SELECT COUNT(*) FROM sim.password_reset_token WHERE usuario_id = :usuarioId")
    long countByUsuarioId(Long usuarioId);
}

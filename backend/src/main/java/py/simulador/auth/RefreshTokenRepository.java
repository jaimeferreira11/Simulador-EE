package py.simulador.auth;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, Long> {

    @Query("SELECT * FROM sim.refresh_token WHERE token_hash = :tokenHash AND revocado = FALSE")
    Optional<RefreshToken> findByTokenHashAndNotRevoked(String tokenHash);

    @Modifying
    @Query("UPDATE sim.refresh_token SET revocado = TRUE WHERE usuario_id = :usuarioId AND revocado = FALSE")
    void revokeAllByUsuarioId(Long usuarioId);
}

package py.simulador.usuario;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends CrudRepository<UsuarioEntity, Long> {

    @Query("SELECT * FROM sim.usuario WHERE email = :email")
    Optional<UsuarioEntity> findByEmail(String email);
}

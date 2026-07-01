package py.simulador.usuario;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import py.simulador.common.UpdatableEntity;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "usuario")
public class UsuarioEntity implements UpdatableEntity {

    @Id
    private Long id;
    private String email;
    private String passwordHash;
    private String nombreCompleto;
    private Long rolUsuarioId;
    private boolean activo;
    private boolean emailVerificado;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime lastLoginAt;
}

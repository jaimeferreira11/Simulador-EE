package py.simulador.entidad;

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
@Table(schema = "sim", value = "entidad")
public class EntidadEntity implements UpdatableEntity {

    @Id
    private Long id;
    private String nombre;
    private String tipo;
    private String descripcion;
    private String contactoNombre;
    private String contactoEmail;
    private boolean activa;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

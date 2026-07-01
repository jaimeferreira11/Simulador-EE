package py.simulador.catalogo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import py.simulador.common.AuditableEntity;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "rubro")
public class RubroEntity implements AuditableEntity {

    @Id
    private Long id;
    private String codigo;
    private String nombre;
    private String descripcion;
    private String productoNombre;
    private String productoDescripcion;
    private String unidadMedida;
    private boolean activo;
    private OffsetDateTime createdAt;
}

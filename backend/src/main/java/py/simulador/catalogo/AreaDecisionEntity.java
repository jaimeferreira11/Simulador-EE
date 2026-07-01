package py.simulador.catalogo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "area_decision")
public class AreaDecisionEntity {

    @Id
    private Long id;
    private String codigo;
    private String nombre;
    private String descripcion;
    private String[] campos;
    private short orden;
    private boolean activo;
}

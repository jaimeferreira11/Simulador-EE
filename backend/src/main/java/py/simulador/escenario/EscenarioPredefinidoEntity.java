package py.simulador.escenario;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "escenario_predefinido")
public class EscenarioPredefinidoEntity {

    @Id
    private Long id;
    private String nombre;
    private String descripcion;
    private int numTrimestres;
    private long cajaInicial;
    private int capacidadInicial;
    private int headcountInicial;
    private long salarioInicial;
    private int inventarioInicial;
    private long valorPlantaInicial;
    private String dificultad;
    private boolean activo;
    private Long rubroId;
    private OffsetDateTime createdAt;
}

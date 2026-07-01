package py.simulador.catalogo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "evento_catalogo")
public class EventoCatalogoEntity {

    @Id
    private Long id;
    private String codigo;
    private String nombre;
    private String descripcion;
    private String severidad;
    private String tipoEfecto;
    private BigDecimal magnitudDefault;
    private short duracionQ;
    private boolean requiereAnuncioPrevio;
    private boolean activo;

    private BigDecimal overridePesoPrecio;
    private BigDecimal overridePesoMarketing;
    private BigDecimal overridePesoCalidad;
    private BigDecimal overridePesoMarca;

    private Long rubroId;
}

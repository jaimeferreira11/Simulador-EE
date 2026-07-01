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
@Table(schema = "sim", value = "parametro_macro_trimestre")
public class ParametroMacroTrimestreEntity {

    @Id
    private Long id;
    private Long macroId;
    private int trimestre;
    private BigDecimal inflacionTrim;
    private BigDecimal tipoCambio;
    private BigDecimal tpmAnual;
}

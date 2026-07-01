package py.simulador.escenario;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "escenario_evento")
public class EscenarioEventoEntity {

    @Id
    private Long id;
    private Long escenarioId;
    private Long eventoCatalogoId;
    private int trimestreNumero;
}

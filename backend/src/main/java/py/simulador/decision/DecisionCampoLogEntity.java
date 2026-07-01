package py.simulador.decision;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "decision_campo_log")
public class DecisionCampoLogEntity {

    @Id
    private Long id;
    private Long decisionEquipoId;
    private String campo;
    private String valorAnterior;
    private String valorNuevo;
    private Long usuarioId;
    private OffsetDateTime modificadoAt;
}

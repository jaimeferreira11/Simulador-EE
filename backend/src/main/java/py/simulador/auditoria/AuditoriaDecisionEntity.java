package py.simulador.auditoria;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "auditoria_decision")
public class AuditoriaDecisionEntity {

    @Id
    private Long id;
    private Long decisionEquipoId;
    private Long usuarioId;
    private String accion;
    private String estadoAnterior;
    private String estadoNuevo;
    private String ipOrigen;
    private OffsetDateTime ocurridoAt;
}

package py.simulador.decision;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "decision_equipo")
public class DecisionEquipoEntity {

    @Id
    private Long id;
    private Long equipoId;
    private Long trimestreId;
    private Long registradoPorUsuarioId;

    private long prestamoSolicitado;
    private long dividendosPagar;

    private long produccionPlanificada;
    private long inversionCapacidad;

    private long precioVenta;
    private long inversionMarketing;

    private short contratacionesNetas;
    private BigDecimal aumentoSalarialPct;
    private long inversionCapacitacion;

    private long inversionId;

    private String estado;
    private OffsetDateTime submittedAt;
    private OffsetDateTime updatedAt;
}

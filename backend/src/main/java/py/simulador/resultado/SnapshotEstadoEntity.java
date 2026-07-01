package py.simulador.resultado;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import py.simulador.common.AuditableEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "snapshot_estado")
public class SnapshotEstadoEntity implements AuditableEntity {

    @Id
    private Long id;
    private Long equipoId;
    private Long trimestreId;
    private String momento;

    private long caja;
    private long deuda;
    private long patrimonioNeto;
    private long valorPlanta;

    private long capacidad;
    private short headcount;
    private long salario;
    private long inventario;

    private BigDecimal brandEquity;
    private BigDecimal calidadPercibida;
    private long idAcumulado;

    private BigDecimal pip;

    private OffsetDateTime createdAt;
}

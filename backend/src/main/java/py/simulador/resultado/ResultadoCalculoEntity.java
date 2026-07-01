package py.simulador.resultado;

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
@Table(schema = "sim", value = "resultado_calculo")
public class ResultadoCalculoEntity {

    @Id
    private Long id;
    private Long equipoId;
    private Long trimestreId;

    private BigDecimal utilizacionCapacidad;
    private BigDecimal factorEficiencia;
    private long produccionReal;

    private long demandaTotalMercado;
    private long demandaAsignada;
    private BigDecimal competitividad;
    private BigDecimal share;
    private long ventasUnidades;

    private long ingresos;

    private long costoMpTotal;
    private long costoLaboral;
    private long costoFijo;
    private long costoMarketing;
    private long costoId;
    private long costoCapacitacion;
    private long costoAlmacenamiento;
    private long depreciacion;
    private long intereses;
    private long costosOperativosTotal;

    private long utilidadOperativa;
    private long utilidadAntesImpuestos;
    private long impuestoIre;
    private long utilidadNeta;
    private BigDecimal pipTrimestre;

    private OffsetDateTime calculadoAt;
}

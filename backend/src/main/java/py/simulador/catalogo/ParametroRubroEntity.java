package py.simulador.catalogo;

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
@Table(schema = "sim", value = "parametro_rubro")
public class ParametroRubroEntity implements AuditableEntity {

    @Id
    private Long id;
    private Long rubroId;
    private String codigo;

    private long demandaBaseTrim;
    private long precioReferencia;

    private BigDecimal elasticidadPrecio;
    private BigDecimal elasticidadMarketing;
    private BigDecimal elasticidadCalidad;

    private BigDecimal pesoPrecio;
    private BigDecimal pesoMarketing;
    private BigDecimal pesoCalidad;
    private BigDecimal pesoMarca;

    private long costoUnitMp;
    private BigDecimal pctMpImportada;
    private long costosFijosTrim;
    private BigDecimal depreciacionTrim;
    private long costoExpansionCapacidad;

    private long salarioPromedioSector;
    private long productividadEmpleado;

    private BigDecimal brandEquityInicial;
    private BigDecimal decaimientoBe;

    // Estacionalidad moved to parametro_rubro_trimestre

    // Spread sobre TPM para calcular tasa activa trimestral
    private BigDecimal spreadTasa;

    private boolean activo;
    private OffsetDateTime createdAt;
}

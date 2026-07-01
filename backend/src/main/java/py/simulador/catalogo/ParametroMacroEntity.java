package py.simulador.catalogo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import py.simulador.common.AuditableEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "parametro_macro")
public class ParametroMacroEntity implements AuditableEntity {

    @Id
    private Long id;
    private String nombreSet;
    private LocalDate vigenteDesde;

    // Quarterly params (inflacion, tipoCambio, tpmAnual) moved to parametro_macro_trimestre

    private long salarioMinimoQ1;
    private long salarioMinimoQ4;

    private BigDecimal ipsPatronal;
    private BigDecimal ipsTrabajador;
    private BigDecimal aguinaldoFactor;
    private BigDecimal tasaIre;
    private BigDecimal ivaGeneral;

    private boolean activo;
    private OffsetDateTime createdAt;
}

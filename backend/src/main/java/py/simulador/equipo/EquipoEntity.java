package py.simulador.equipo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import py.simulador.common.AuditableEntity;
import py.simulador.config.JsonbValue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "equipo")
public class EquipoEntity implements AuditableEntity {

    @Id
    private Long id;
    private Long competenciaId;
    private String nombreEmpresa;
    private String codigoColor;
    private String estado;
    private Short posicionFinal;
    private BigDecimal pipFinal;
    private boolean enBancarrota;
    private Integer trimestreBancarrota;
    private String tipo = "HUMANO";
    private String dificultad;
    private String personalidad;

    @Column("bot_config")
    private JsonbValue botConfig;

    private OffsetDateTime createdAt;

    public boolean esBot() {
        return "BOT".equals(tipo);
    }
}

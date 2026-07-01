package py.simulador.evento;

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
@Table(schema = "sim", value = "evento_competencia")
public class EventoCompetenciaEntity implements AuditableEntity {

    @Id
    private Long id;
    private Long competenciaId;
    private Long trimestreId;
    private Long eventoCatalogoId;
    private String origen;
    private Long disparadoPorUsuarioId;
    private BigDecimal magnitudAplicada;
    private short duracionAplicada;
    private String justificacion;
    private OffsetDateTime createdAt;
}

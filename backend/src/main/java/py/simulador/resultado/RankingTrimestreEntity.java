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
@Table(schema = "sim", value = "ranking_trimestre")
public class RankingTrimestreEntity {

    @Id
    private Long id;
    private Long competenciaId;
    private Long trimestreId;
    private Long equipoId;
    private short posicion;
    private BigDecimal pipAcumulado;
    private long utilidadAcumulada;
    private long cajaActual;
    private BigDecimal shareActual;
    private OffsetDateTime calculadoAt;
}

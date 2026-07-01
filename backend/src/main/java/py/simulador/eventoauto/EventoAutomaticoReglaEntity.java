package py.simulador.eventoauto;

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
@Table(schema = "sim", value = "evento_automatico_regla")
public class EventoAutomaticoReglaEntity {

    @Id
    private Long id;
    private String nombre;
    private String descripcion;
    private String condicionTipo;
    private BigDecimal condicionUmbral;
    private String condicionOperador;
    private BigDecimal probabilidad;
    private String efectoTipo;
    private BigDecimal efectoValor;
    private int duracionTrimestres;
    private boolean activo;
    private OffsetDateTime createdAt;
}

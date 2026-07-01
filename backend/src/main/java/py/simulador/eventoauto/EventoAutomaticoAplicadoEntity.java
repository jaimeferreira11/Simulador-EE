package py.simulador.eventoauto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "evento_automatico_aplicado")
public class EventoAutomaticoAplicadoEntity {

    @Id
    private Long id;
    private Long competenciaId;
    private Long equipoId;
    private Long reglaId;
    private int trimestreOrigen;
    private int trimestreEfectoInicio;
    private int trimestreEfectoFin;
    private OffsetDateTime createdAt;
}

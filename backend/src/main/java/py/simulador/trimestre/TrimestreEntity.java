package py.simulador.trimestre;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "trimestre")
public class TrimestreEntity {

    @Id
    private Long id;
    private Long competenciaId;
    private short numero;
    private String estado;
    private OffsetDateTime aperturaAt;
    private OffsetDateTime cierreAt;
    private OffsetDateTime procesadoAt;
}

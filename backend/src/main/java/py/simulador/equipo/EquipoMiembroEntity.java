package py.simulador.equipo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "equipo_miembro")
public class EquipoMiembroEntity {

    @Id
    private Long id;
    private Long equipoId;
    private Long usuarioId;
    private Long areaId;
    private boolean esCapitan;
    private OffsetDateTime joinedAt;
}

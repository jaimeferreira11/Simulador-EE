package py.simulador.auditoria;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "auditoria_evento")
public class AuditoriaEventoEntity {

    @Id
    private Long id;
    private Long competenciaId;
    private Long usuarioId;
    private String tipoAccion;
    private String descripcion;
    private String datosContexto;
    private String ipOrigen;
    private OffsetDateTime ocurridoAt;
}

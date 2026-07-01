package py.simulador.notificacion;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "notificacion")
public class NotificacionEntity {

    @Id
    private Long id;
    private Long usuarioId;
    private Long competenciaId;
    private String tipo;
    private String titulo;
    private String descripcion;
    private String severidad;
    private boolean leida;
    private OffsetDateTime createdAt;
}

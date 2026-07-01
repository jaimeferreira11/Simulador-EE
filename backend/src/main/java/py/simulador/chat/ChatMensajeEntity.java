package py.simulador.chat;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "chat_mensaje")
public class ChatMensajeEntity {

    @Id
    private Long id;
    private Long competenciaId;
    private Long equipoId;
    private Long usuarioId;
    private String contenido;
    private OffsetDateTime createdAt;
}

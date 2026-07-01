package py.simulador.asistente;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "asistente_consulta_log")
public class AsistenteConsultaLogEntity {

    @Id
    private Long id;

    @Column("competencia_id")
    private Long competenciaId;

    @Column("usuario_id")
    private Long usuarioId;

    private String pregunta;

    @Column("hubo_match")
    private boolean huboMatch;

    @Column("faq_id")
    private Long faqId;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("origen")
    private String origen;
}

package py.simulador.asistente;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "asistente_faq")
public class AsistenteFaqEntity {

    @Id
    private Long id;

    private String pregunta;
    private String respuesta;
    private String[] keywords;

    @Column("seccion_manual")
    private String seccionManual;

    private int orden;
    private boolean activa;
}

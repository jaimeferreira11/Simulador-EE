package py.simulador.llm;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "coaching_trimestre")
public class CoachingTrimestreEntity {
    @Id
    private Long id;
    private Long trimestreId;
    private Long equipoId;
    private String texto;
    private OffsetDateTime createdAt;
}

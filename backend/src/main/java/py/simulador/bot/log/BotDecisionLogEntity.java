package py.simulador.bot.log;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

/**
 * Fila de auditoría por cada generación de decisión de bot EXPERTO (Fase 2).
 *
 * <p>Solo se persiste para bots EXPERTO. Los heurísticos (FACIL/MEDIO/DIFICIL)
 * no producen filas — son determinísticos y rápidos, basta con logs de aplicación.
 *
 * <p>Mapea a {@code sim.bot_decision_log} (migración V202605121300).
 */
@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "bot_decision_log")
public class BotDecisionLogEntity {

    @Id
    private Long id;
    private Long equipoId;
    private Long trimestreId;
    /** HEURISTIC | LLM | LLM_FALLBACK */
    private String strategyUsed;
    private Integer latencyMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private String fallbackReason;
    private OffsetDateTime createdAt;
}

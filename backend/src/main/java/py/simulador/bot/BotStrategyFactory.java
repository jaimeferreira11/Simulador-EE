package py.simulador.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import py.simulador.bot.model.Difficulty;
import py.simulador.bot.model.Personality;
import py.simulador.bot.strategy.HeuristicStrategy;
import py.simulador.bot.strategy.LlmBotStrategy;
import py.simulador.llm.LlmProvider;

@Component
public class BotStrategyFactory {

    private final LlmProvider llmProvider;
    private final ObjectMapper objectMapper;

    /**
     * Constructor sin dependencias — usado solo desde tests unitarios que
     * construyen estrategias heurísticas sin necesitar el pipeline LLM.
     */
    public BotStrategyFactory() {
        this.llmProvider = null;
        this.objectMapper = null;
    }

    public BotStrategyFactory(LlmProvider llmProvider, ObjectMapper objectMapper) {
        this.llmProvider = llmProvider;
        this.objectMapper = objectMapper;
    }

    public BotStrategy forDifficulty(Difficulty d, Personality p) {
        return switch (d) {
            case FACIL, MEDIO, DIFICIL -> new HeuristicStrategy(d, p);
            case EXPERTO -> {
                if (llmProvider == null || objectMapper == null) {
                    // Defensivo: si la factory fue construida sin pipeline LLM
                    // (tests unitarios viejos), degradamos a heurística DIFICIL
                    // con la misma personalidad — equivalente al fallback que
                    // hace LlmBotStrategy ante un LLM caído.
                    yield new HeuristicStrategy(Difficulty.DIFICIL, p);
                }
                yield new LlmBotStrategy(llmProvider, p, objectMapper);
            }
        };
    }
}

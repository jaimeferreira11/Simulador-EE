package py.simulador.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import py.simulador.bot.model.Difficulty;
import py.simulador.bot.model.Personality;
import py.simulador.bot.strategy.HeuristicStrategy;
import py.simulador.bot.strategy.LlmBotStrategy;
import py.simulador.llm.LlmProvider;

import static org.assertj.core.api.Assertions.assertThat;

class BotStrategyFactoryTest {

    @Test
    void returns_heuristic_strategy_for_facil_medio_dificil() {
        var factory = new BotStrategyFactory();
        for (Difficulty d : new Difficulty[]{Difficulty.FACIL, Difficulty.MEDIO, Difficulty.DIFICIL}) {
            for (Personality p : Personality.values()) {
                BotStrategy s = factory.forDifficulty(d, p);
                assertThat(s).isInstanceOf(HeuristicStrategy.class);
            }
        }
    }

    @Test
    void returns_llm_strategy_for_experto_when_pipeline_available() {
        LlmProvider provider = Mockito.mock(LlmProvider.class);
        ObjectMapper mapper = new ObjectMapper();
        var factory = new BotStrategyFactory(provider, mapper);

        for (Personality p : Personality.values()) {
            BotStrategy s = factory.forDifficulty(Difficulty.EXPERTO, p);
            assertThat(s).isInstanceOf(LlmBotStrategy.class);
        }
    }

    @Test
    void experto_degrades_to_heuristic_dificil_when_pipeline_missing() {
        // Constructor sin pipeline (caso defensivo / tests legacy)
        var factory = new BotStrategyFactory();
        BotStrategy s = factory.forDifficulty(Difficulty.EXPERTO, Personality.PREMIUM);
        assertThat(s).isInstanceOf(HeuristicStrategy.class);
    }
}

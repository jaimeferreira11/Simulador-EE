package py.simulador.bot;

import org.junit.jupiter.api.Test;
import py.simulador.bot.model.Personality;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BotPersonalityAssignerTest {

    @Test
    void assigns_first_personality_when_no_bots_exist() {
        var assigner = new BotPersonalityAssigner();
        Personality p = assigner.assignFor(List.of());
        assertThat(p).isEqualTo(Personality.COST_LEADER);
    }

    @Test
    void assigns_least_represented_personality() {
        var assigner = new BotPersonalityAssigner();
        Personality p = assigner.assignFor(List.of(Personality.COST_LEADER, Personality.PREMIUM));
        assertThat(p).isEqualTo(Personality.BALANCEADO);
    }

    @Test
    void cycles_through_when_all_equal() {
        var assigner = new BotPersonalityAssigner();
        Personality p = assigner.assignFor(List.of(
            Personality.COST_LEADER, Personality.PREMIUM, Personality.BALANCEADO));
        assertThat(p).isIn((Object[]) Personality.values());
    }
}

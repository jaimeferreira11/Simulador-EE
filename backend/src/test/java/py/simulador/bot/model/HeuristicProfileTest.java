package py.simulador.bot.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HeuristicProfileTest {

    @Test
    void cost_leader_has_low_markup_and_low_marketing() {
        var p = HeuristicProfile.baseFor(Personality.COST_LEADER);
        assertThat(p.markupOverCost()).isBetween(1.15, 1.20);
        assertThat(p.marketingShareOfRevenue()).isBetween(0.05, 0.08);
        assertThat(p.rdShareOfRevenue()).isBetween(0.03, 0.05);
    }

    @Test
    void premium_has_high_markup_and_high_rd() {
        var p = HeuristicProfile.baseFor(Personality.PREMIUM);
        assertThat(p.markupOverCost()).isBetween(1.50, 1.70);
        assertThat(p.marketingShareOfRevenue()).isBetween(0.15, 0.20);
        assertThat(p.rdShareOfRevenue()).isBetween(0.12, 0.18);
    }

    @Test
    void balanceado_sits_in_the_middle() {
        var p = HeuristicProfile.baseFor(Personality.BALANCEADO);
        assertThat(p.markupOverCost()).isBetween(1.30, 1.40);
        assertThat(p.marketingShareOfRevenue()).isBetween(0.10, 0.12);
        assertThat(p.rdShareOfRevenue()).isBetween(0.07, 0.10);
    }

    @Test
    void facil_has_no_flex_and_no_reactivity() {
        var l = HeuristicProfile.levelFor(Difficulty.FACIL);
        assertThat(l.flexFactor()).isEqualTo(0.0);
        assertThat(l.eventReactivity()).isEqualTo(0.0);
        assertThat(l.safetyNetsEnabled()).isFalse();
    }

    @Test
    void dificil_has_max_flex_and_reactivity() {
        var l = HeuristicProfile.levelFor(Difficulty.DIFICIL);
        assertThat(l.flexFactor()).isEqualTo(0.30);
        assertThat(l.eventReactivity()).isEqualTo(1.0);
        assertThat(l.safetyNetsEnabled()).isTrue();
    }
}

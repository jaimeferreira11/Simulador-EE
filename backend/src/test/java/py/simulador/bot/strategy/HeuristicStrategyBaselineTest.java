package py.simulador.bot.strategy;

import org.junit.jupiter.api.Test;
import py.simulador.bot.BotContext;
import py.simulador.bot.BotDecisionDTO;
import py.simulador.bot.model.Difficulty;
import py.simulador.bot.model.Personality;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicStrategyBaselineTest {

    private BotContext baseCtx(Personality p, Difficulty d) {
        return new BotContext(
            1L, 100L, d, p,
            5_000_000L,    // caja
            0L,            // inventario
            0.5,           // brand equity
            0L,            // rd acumulado
            10_000L,       // costo unitario
            BotContext.CAPACIDAD_DESCONOCIDA, // capacidad (clamp disabled)
            null, null, null, 4,
            Map.of(),
            List.of(),
            List.of()
        );
    }

    @Test
    void cost_leader_facil_uses_low_markup() {
        var strat = new HeuristicStrategy(Difficulty.FACIL, Personality.COST_LEADER);
        BotDecisionDTO d = strat.generate(baseCtx(Personality.COST_LEADER, Difficulty.FACIL));
        assertThat(d.precioUnitario()).isBetween(11_500L, 12_100L);
    }

    @Test
    void premium_facil_uses_high_markup_and_high_rd() {
        var strat = new HeuristicStrategy(Difficulty.FACIL, Personality.PREMIUM);
        var ctx = baseCtx(Personality.PREMIUM, Difficulty.FACIL);
        BotDecisionDTO d = strat.generate(ctx);
        assertThat(d.precioUnitario()).isBetween(15_500L, 16_500L);
        assertThat(d.inversionRd()).isGreaterThan(0L);
    }

    @Test
    void balanceado_facil_sits_in_the_middle() {
        var strat = new HeuristicStrategy(Difficulty.FACIL, Personality.BALANCEADO);
        BotDecisionDTO d = strat.generate(baseCtx(Personality.BALANCEADO, Difficulty.FACIL));
        assertThat(d.precioUnitario()).isBetween(13_000L, 14_000L);
    }

    @Test
    void all_decisions_are_non_negative() {
        for (Personality p : Personality.values()) {
            var strat = new HeuristicStrategy(Difficulty.FACIL, p);
            BotDecisionDTO d = strat.generate(baseCtx(p, Difficulty.FACIL));
            assertThat(d.precioUnitario()).isPositive();
            assertThat(d.produccionUnidades()).isNotNegative();
            assertThat(d.inversionMarketing()).isNotNegative();
            assertThat(d.inversionRd()).isNotNegative();
            assertThat(d.cantidadEmpleados()).isPositive();
        }
    }

    @Test
    void output_is_deterministic() {
        var strat = new HeuristicStrategy(Difficulty.FACIL, Personality.BALANCEADO);
        var ctx = baseCtx(Personality.BALANCEADO, Difficulty.FACIL);
        var d1 = strat.generate(ctx);
        var d2 = strat.generate(ctx);
        assertThat(d1).isEqualTo(d2);
    }
}

package py.simulador.bot.strategy;

import org.junit.jupiter.api.Test;
import py.simulador.bot.BotContext;
import py.simulador.bot.BotDecisionDTO;
import py.simulador.bot.model.Difficulty;
import py.simulador.bot.model.Personality;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicStrategyLevelAdjustmentTest {

    private BotContext ctxWithInventory(long inventario, Difficulty d) {
        return new BotContext(
            1L, 100L, d, Personality.BALANCEADO,
            5_000_000L, inventario, 0.5, 0L, 10_000L,
            BotContext.CAPACIDAD_DESCONOCIDA,
            12_000_000L, 1_500_000L, 2, 4,
            Map.of(), List.of(), List.of()
        );
    }

    @Test
    void facil_ignores_high_inventory() {
        var stratFacil = new HeuristicStrategy(Difficulty.FACIL, Personality.BALANCEADO);
        BotDecisionDTO d = stratFacil.generate(ctxWithInventory(500L, Difficulty.FACIL));
        assertThat(d.produccionUnidades()).isGreaterThanOrEqualTo(1000L);
    }

    @Test
    void medio_reduces_production_when_inventory_above_30_pct() {
        var stratMedio = new HeuristicStrategy(Difficulty.MEDIO, Personality.BALANCEADO);
        var ctxLow = ctxWithInventory(100L, Difficulty.MEDIO);
        var ctxHigh = ctxWithInventory(500L, Difficulty.MEDIO);
        BotDecisionDTO dLow = stratMedio.generate(ctxLow);
        BotDecisionDTO dHigh = stratMedio.generate(ctxHigh);
        assertThat(dHigh.produccionUnidades()).isLessThan(dLow.produccionUnidades());
    }

    @Test
    void dificil_reduces_production_and_drops_price_when_inventory_very_high() {
        var stratDificil = new HeuristicStrategy(Difficulty.DIFICIL, Personality.BALANCEADO);
        var ctxLow = ctxWithInventory(100L, Difficulty.DIFICIL);
        var ctxVeryHigh = ctxWithInventory(800L, Difficulty.DIFICIL);
        BotDecisionDTO dLow = stratDificil.generate(ctxLow);
        BotDecisionDTO dVeryHigh = stratDificil.generate(ctxVeryHigh);
        assertThat(dVeryHigh.produccionUnidades()).isLessThan(dLow.produccionUnidades());
        assertThat(dVeryHigh.precioUnitario()).isLessThan(dLow.precioUnitario());
    }
}

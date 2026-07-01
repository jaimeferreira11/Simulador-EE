package py.simulador.bot.strategy;

import org.junit.jupiter.api.Test;
import py.simulador.bot.BotContext;
import py.simulador.bot.BotDecisionDTO;
import py.simulador.bot.model.Difficulty;
import py.simulador.bot.model.Personality;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicStrategyEventResponseTest {

    private BotContext ctxWithEvent(Difficulty d, BotContext.EventoActivo ev) {
        return new BotContext(
            1L, 100L, d, Personality.BALANCEADO,
            5_000_000L, 0L, 0.5, 0L, 10_000L,
            BotContext.CAPACIDAD_DESCONOCIDA,
            12_000_000L, 1_500_000L, 2, 4,
            Map.of(),
            ev == null ? List.of() : List.of(ev),
            List.of()
        );
    }

    @Test
    void facil_ignores_diesel_event() {
        var dieselUp = new BotContext.EventoActivo("DIESEL_UP_15", "Suba diesel 15%",
            Map.of("costo_unitario_delta", 0.15));
        var stratFacil = new HeuristicStrategy(Difficulty.FACIL, Personality.BALANCEADO);
        BotDecisionDTO sin = stratFacil.generate(ctxWithEvent(Difficulty.FACIL, null));
        BotDecisionDTO con = stratFacil.generate(ctxWithEvent(Difficulty.FACIL, dieselUp));
        assertThat(con.precioUnitario()).isEqualTo(sin.precioUnitario());
    }

    @Test
    void medio_responds_to_diesel_event_by_raising_price_partially() {
        var dieselUp = new BotContext.EventoActivo("DIESEL_UP_15", "Suba diesel 15%",
            Map.of("costo_unitario_delta", 0.15));
        var stratMedio = new HeuristicStrategy(Difficulty.MEDIO, Personality.BALANCEADO);
        BotDecisionDTO sin = stratMedio.generate(ctxWithEvent(Difficulty.MEDIO, null));
        BotDecisionDTO con = stratMedio.generate(ctxWithEvent(Difficulty.MEDIO, dieselUp));
        assertThat(con.precioUnitario()).isGreaterThan(sin.precioUnitario());
    }

    @Test
    void low_cash_triggers_loan_in_dificil_with_safety_nets() {
        var lowCashCtx = new BotContext(
            1L, 100L, Difficulty.DIFICIL, Personality.BALANCEADO,
            500_000L,
            0L, 0.5, 0L, 10_000L,
            BotContext.CAPACIDAD_DESCONOCIDA,
            12_000_000L, -500_000L, 4, 4,
            Map.of(), List.of(), List.of()
        );
        var strat = new HeuristicStrategy(Difficulty.DIFICIL, Personality.BALANCEADO);
        BotDecisionDTO d = strat.generate(lowCashCtx);
        assertThat(d.prestamoSolicitado()).isGreaterThan(0L);
    }
}

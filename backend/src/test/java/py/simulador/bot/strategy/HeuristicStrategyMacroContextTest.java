package py.simulador.bot.strategy;

import org.junit.jupiter.api.Test;
import py.simulador.bot.BotContext;
import py.simulador.bot.BotDecisionDTO;
import py.simulador.bot.model.Difficulty;
import py.simulador.bot.model.Personality;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link HeuristicStrategy} reads market parameters from
 * {@link BotContext#parametrosMacro()} instead of relying on hardcoded
 * literals (FU5).
 *
 * <p>Keys consumed by the strategy:
 * <ul>
 *   <li>{@code demanda_base_trim} — total market demand for the trimestre,
 *       sourced by {@code BotContextBuilder} from the active {@code parametro_rubro}.
 *       The strategy divides by the number of teams to get a per-team baseline.</li>
 *   <li>{@code salario_minimo_q1} — prevailing minimum wage at competition
 *       start, sourced from {@code parametro_macro}. Used as the salary
 *       baseline before applying the personality multiplier.</li>
 * </ul>
 */
class HeuristicStrategyMacroContextTest {

    private BotContext ctxWithMacro(Map<String, Number> macros, Integer totalEquipos) {
        return new BotContext(
            1L, 100L, Difficulty.FACIL, Personality.BALANCEADO,
            5_000_000L,    // caja
            0L,            // inventario
            0.5,           // brand equity
            0L,            // rd acumulado
            10_000L,       // costo unitario
            BotContext.CAPACIDAD_DESCONOCIDA, // capacidad
            null, null, null, totalEquipos,
            macros,
            List.of(),
            List.of()
        );
    }

    @Test
    void estimateDemand_usesContextDemandWhenAvailable() {
        // Total market = 20_000 units, 4 teams => per-team baseline 5_000.
        var ctx = ctxWithMacro(Map.of("demanda_base_trim", 20_000L), 4);
        var strat = new HeuristicStrategy(Difficulty.FACIL, Personality.BALANCEADO);

        BotDecisionDTO d = strat.generate(ctx);

        // Production = baseline (5_000) * BALANCEADO targetMultiplier (1.0) = 5_000.
        // Greater than the legacy 1000/1200 fallback by a wide margin.
        assertThat(d.produccionUnidades()).isGreaterThan(2_000L);
    }

    @Test
    void estimateDemand_fallsBackWhenMacroAbsent() {
        // No demanda_base_trim in macros, no totalEquipos either => legacy
        // per-team fallback (1000 for Q1) so existing behaviour is preserved.
        var ctx = ctxWithMacro(Map.of(), null);
        var strat = new HeuristicStrategy(Difficulty.FACIL, Personality.BALANCEADO);

        BotDecisionDTO d = strat.generate(ctx);

        // BALANCEADO targetMultiplier is 1.0 => production ~ 1000.
        assertThat(d.produccionUnidades()).isBetween(900L, 1_100L);
    }

    @Test
    void salario_usesContextSalarioMinimoWhenAvailable() {
        // Map provides salario_minimo_q1 = 3_000_000; BALANCEADO multiplier is 1.0.
        var ctx = ctxWithMacro(Map.of("salario_minimo_q1", 3_000_000L), 4);
        var strat = new HeuristicStrategy(Difficulty.FACIL, Personality.BALANCEADO);

        BotDecisionDTO d = strat.generate(ctx);

        assertThat(d.salarioPromedio()).isEqualTo(3_000_000L);
    }

    @Test
    void salario_fallsBackWhenMacroAbsent() {
        // No salario key => legacy fallback 2_700_000 * 1.0 (BALANCEADO) = 2_700_000.
        var ctx = ctxWithMacro(Map.of(), 4);
        var strat = new HeuristicStrategy(Difficulty.FACIL, Personality.BALANCEADO);

        BotDecisionDTO d = strat.generate(ctx);

        assertThat(d.salarioPromedio()).isEqualTo(2_700_000L);
    }
}

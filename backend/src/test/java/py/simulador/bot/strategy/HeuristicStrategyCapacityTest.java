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
 * Verifies that {@link HeuristicStrategy} caps the planned production at the
 * equipo's actual capacity (the snapshot's {@code capacidad} at INICIO of the
 * trimestre).
 *
 * <p>Background — without this clamp the heuristic plans a production target
 * derived only from market demand share and the personality multiplier; in
 * later quarters that number will silently exceed the team's depreciated
 * capacity and the simulation engine will then truncate it. The bot ends up
 * "wasting" planning intent on units it cannot make.
 *
 * <p>Two complementary expectations:
 * <ul>
 *   <li>Aggressive personalities (e.g. {@link Personality#COST_LEADER},
 *       multiplier {@code 1.10}) must NOT exceed 100% of capacity even though
 *       their multiplier alone would push them over.</li>
 *   <li>Conservative personalities (e.g. {@link Personality#PREMIUM},
 *       multiplier {@code 0.95}) must stay BELOW capacity — the multiplier
 *       acts as the headroom buffer and is preserved by the clamp.</li>
 * </ul>
 */
class HeuristicStrategyCapacityTest {

    /** Builds a context with very high market demand so production is
     *  capacity-bound (not demand-bound) — that's the case we want to test. */
    private BotContext baseCtx(Personality p, Difficulty d) {
        return new BotContext(
            1L, 100L, d, p,
            5_000_000L,    // caja
            0L,            // inventario
            0.5,           // brand equity
            0L,            // rd acumulado
            10_000L,       // costo unitario
            BotContext.CAPACIDAD_DESCONOCIDA, // overridden by withCapacity()
            null, null, null, 4,
            // 4_000_000 / 4 teams = 1_000_000 per-team baseline -> exceeds any
            // reasonable plant capacity, so the clamp is what determines output.
            Map.of("demanda_base_trim", 4_000_000L),
            List.of(),
            List.of()
        );
    }

    private BotContext withCapacity(BotContext ctx, long capacidad) {
        return new BotContext(
            ctx.equipoId(), ctx.trimestreId(), ctx.difficulty(), ctx.personality(),
            ctx.cajaActual(), ctx.inventarioUnidades(), ctx.brandEquity(),
            ctx.rdAcumulado(), ctx.costoUnitarioEstimado(),
            capacidad,
            ctx.ingresoTrimestreAnterior(), ctx.gananciaTrimestreAnterior(),
            ctx.posicionRankingAnterior(), ctx.totalEquipos(),
            ctx.parametrosMacro(), ctx.eventosActivos(), ctx.competidores()
        );
    }

    @Test
    void produccion_clampedToCapacidadActual_evenForCostLeader() {
        // COST_LEADER targetProductionMultiplier = 1.10, would push above
        // capacity. Clamp must keep it at <= capacidadActual.
        var ctx = withCapacity(baseCtx(Personality.COST_LEADER, Difficulty.DIFICIL), 30_000L);
        var strat = new HeuristicStrategy(Difficulty.DIFICIL, Personality.COST_LEADER);

        BotDecisionDTO d = strat.generate(ctx);

        assertThat(d.produccionUnidades()).isLessThanOrEqualTo(30_000L);
    }

    @Test
    void premium_aims_below_capacity_to_keep_buffer() {
        // PREMIUM targetProductionMultiplier = 0.95 — preserve the headroom:
        // produccion should be at most 95% of capacity.
        var ctx = withCapacity(baseCtx(Personality.PREMIUM, Difficulty.MEDIO), 50_000L);
        var strat = new HeuristicStrategy(Difficulty.MEDIO, Personality.PREMIUM);

        BotDecisionDTO d = strat.generate(ctx);

        assertThat(d.produccionUnidades()).isLessThanOrEqualTo(50_000L * 95 / 100);
    }

    @Test
    void no_clamp_when_capacity_unknown_sentinel() {
        // Sentinel disables the clamp — production stays driven by demand share.
        // demanda total 4_000_000 / 4 teams = 1_000_000 per-team baseline,
        // BALANCEADO multiplier = 1.0 -> ~1_000_000 units (would never get
        // there if a sentinel-as-cap was applied because Long.MAX_VALUE * 1.0
        // overflows; we verify both: positive AND well above any 'tiny' value).
        var ctx = baseCtx(Personality.BALANCEADO, Difficulty.FACIL);
        var strat = new HeuristicStrategy(Difficulty.FACIL, Personality.BALANCEADO);

        BotDecisionDTO d = strat.generate(ctx);

        assertThat(d.produccionUnidades()).isGreaterThan(100_000L);
    }

    @Test
    void capacity_drop_quarter_over_quarter_reduces_planned_production() {
        // Reproduces the E2E bug: same demand, same personality, but capacity
        // drops 50_000 -> 31_500 between Qs. Production must follow.
        var stratQ1 = new HeuristicStrategy(Difficulty.DIFICIL, Personality.COST_LEADER);
        var ctxQ1 = withCapacity(baseCtx(Personality.COST_LEADER, Difficulty.DIFICIL), 50_000L);
        var ctxQ4 = withCapacity(baseCtx(Personality.COST_LEADER, Difficulty.DIFICIL), 31_500L);

        BotDecisionDTO dQ1 = stratQ1.generate(ctxQ1);
        BotDecisionDTO dQ4 = stratQ1.generate(ctxQ4);

        assertThat(dQ1.produccionUnidades()).isLessThanOrEqualTo(50_000L);
        assertThat(dQ4.produccionUnidades()).isLessThanOrEqualTo(31_500L);
        assertThat(dQ4.produccionUnidades()).isLessThan(dQ1.produccionUnidades());
    }
}

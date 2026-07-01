package py.simulador.bot.strategy;

import py.simulador.bot.BotContext;
import py.simulador.bot.BotDecisionDTO;
import py.simulador.bot.BotStrategy;
import py.simulador.bot.model.Difficulty;
import py.simulador.bot.model.HeuristicProfile;
import py.simulador.bot.model.HeuristicProfile.LevelMultipliers;
import py.simulador.bot.model.HeuristicProfile.ProfileCoefficients;
import py.simulador.bot.model.Personality;

public class HeuristicStrategy implements BotStrategy {

    private final Difficulty difficulty;
    private final Personality personality;

    public HeuristicStrategy(Difficulty difficulty, Personality personality) {
        this.difficulty = difficulty;
        this.personality = personality;
    }

    @Override
    public BotDecisionDTO generate(BotContext ctx) {
        ProfileCoefficients base = HeuristicProfile.baseFor(personality);
        LevelMultipliers level = HeuristicProfile.levelFor(difficulty);

        long demandaEstimada = estimateDemand(ctx);
        double inventarioRatio = demandaEstimada == 0
            ? 0.0
            : (double) ctx.inventarioUnidades() / demandaEstimada;

        // Produccion base
        long produccion = Math.round(demandaEstimada * base.targetProductionMultiplier());

        // Ajuste por inventario (solo niveles con flexFactor > 0)
        if (level.flexFactor() > 0 && inventarioRatio > 0.30) {
            double reduction = Math.min(0.5, (inventarioRatio - 0.30) * level.flexFactor() * 2);
            produccion = Math.round(produccion * (1 - reduction));
        }

        // Clamp a la capacidad real del equipo. Sin esto el bot planifica
        // unidades que la planta no puede fabricar (la depreciacion baja la
        // capacidad trimestre a trimestre) y el motor recorta silenciosamente.
        // - COST_LEADER tiene targetMultiplier 1.10 -> usamos min(mult, 1.0)
        //   para que pegue al 100% de capacidad, no por encima.
        // - PREMIUM tiene targetMultiplier 0.95 -> el factor preserva el
        //   colchon, dejando ~5% de capacidad libre como buffer de calidad.
        // El sentinel CAPACIDAD_DESCONOCIDA (Long.MAX_VALUE) deshabilita el
        // clamp: contextos sin snapshot (Q1 antes de procesar, tests viejos).
        if (ctx.capacidadActual() != BotContext.CAPACIDAD_DESCONOCIDA
                && ctx.capacidadActual() > 0) {
            double capFactor = Math.min(base.targetProductionMultiplier(), 1.0);
            long capCap = Math.round(ctx.capacidadActual() * capFactor);
            produccion = Math.min(produccion, capCap);
        }

        // Precio base
        long precio = Math.round(ctx.costoUnitarioEstimado() * base.markupOverCost());

        // Event response (proporcional a level.eventReactivity)
        if (level.eventReactivity() > 0) {
            for (BotContext.EventoActivo ev : ctx.eventosActivos()) {
                Number costoDelta = ev.efectos().get("costo_unitario_delta");
                if (costoDelta != null) {
                    double passThrough = 0.5 * level.eventReactivity();
                    precio = Math.round(precio * (1 + costoDelta.doubleValue() * passThrough));
                }
            }
        }

        // DIFICIL: baja precio si inventario muy alto (>40%)
        if (difficulty == Difficulty.DIFICIL && inventarioRatio > 0.40) {
            precio = Math.round(precio * 0.92);
        }

        long ingresoEstimado = precio * produccion;
        long marketing = Math.round(ingresoEstimado * base.marketingShareOfRevenue());
        long rd = Math.round(ingresoEstimado * base.rdShareOfRevenue());

        int empleados = Math.max(2, (int) Math.round(produccion / 1000.0));

        // Salary baseline: prefer the macro-driven minimum wage exposed by the
        // BotContextBuilder. Fallback only kicks in when the context has no
        // macros wired (e.g. unit tests with empty maps, or first runs before
        // a parametro_macro set is attached to the competencia).
        Number salarioMinimo = ctx.parametrosMacro().get("salario_minimo_q1");
        long salarioBase = salarioMinimo != null
            ? salarioMinimo.longValue()
            : 2_700_000L; // MTESS minimum wage 2024 — last-resort default
        long salario = Math.round(salarioBase * base.salaryRelativeToMarket());

        // Safety net
        long prestamo = 0L;
        if (level.safetyNetsEnabled() && ctx.cajaActual() < 1_000_000L) {
            prestamo = 5_000_000L;
        }

        return new BotDecisionDTO(
            precio, produccion, marketing, rd,
            empleados, salario, prestamo, 0L,
            String.format("%s/%s invRatio=%.2f cash=%d events=%d",
                difficulty, personality, inventarioRatio, ctx.cajaActual(), ctx.eventosActivos().size())
        );
    }

    /**
     * Per-team demand baseline.
     *
     * <p>Reads the total market demand for the trimestre from
     * {@code parametrosMacro["demanda_base_trim"]} (sourced by
     * {@code BotContextBuilder} from the active {@code parametro_rubro})
     * and divides it by the number of teams in the competition so each bot
     * targets a fair share.
     *
     * <p>If the macro is missing — only expected during early Phase 1 runs
     * before a rubro/macro set is attached to the competencia, or in unit
     * tests with empty maps — falls back to the legacy conservative
     * estimates (1000 for Q1, 1200 onwards).
     */
    private long estimateDemand(BotContext ctx) {
        Number demandaTotal = ctx.parametrosMacro().get("demanda_base_trim");
        if (demandaTotal != null) {
            int equipos = ctx.totalEquipos() != null && ctx.totalEquipos() > 0
                ? ctx.totalEquipos()
                : Math.max(1, ctx.competidores().size() + 1);
            return Math.max(1L, demandaTotal.longValue() / equipos);
        }
        return ctx.esPrimerTrimestre() ? 1000L : 1200L;
    }
}

package py.simulador.bot.model;

import java.util.Map;

public final class HeuristicProfile {

    public record ProfileCoefficients(
        double markupOverCost,
        double marketingShareOfRevenue,
        double rdShareOfRevenue,
        double salaryRelativeToMarket,
        double targetProductionMultiplier
    ) {}

    public record LevelMultipliers(
        double flexFactor,
        double eventReactivity,
        boolean safetyNetsEnabled
    ) {}

    private static final Map<Personality, ProfileCoefficients> BASE = Map.of(
        Personality.COST_LEADER, new ProfileCoefficients(1.18, 0.06, 0.04, 0.95, 1.10),
        Personality.PREMIUM,     new ProfileCoefficients(1.60, 0.18, 0.15, 1.10, 0.95),
        Personality.BALANCEADO,  new ProfileCoefficients(1.35, 0.11, 0.09, 1.00, 1.00)
    );

    private static final Map<Difficulty, LevelMultipliers> LEVEL = Map.of(
        Difficulty.FACIL,   new LevelMultipliers(0.0,  0.0, false),
        Difficulty.MEDIO,   new LevelMultipliers(0.15, 0.5, true),
        Difficulty.DIFICIL, new LevelMultipliers(0.30, 1.0, true)
    );

    private HeuristicProfile() {}

    public static ProfileCoefficients baseFor(Personality p) {
        return BASE.get(p);
    }

    public static LevelMultipliers levelFor(Difficulty d) {
        return LEVEL.get(d);
    }
}

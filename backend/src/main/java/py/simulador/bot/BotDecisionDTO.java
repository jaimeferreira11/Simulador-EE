package py.simulador.bot;

/**
 * DTO de salida del strategy. Contiene los ~10-12 campos de decisión
 * que esperaría un trimestre. El mapper convierte a DecisionEquipoEntity
 * antes de persistir.
 */
public record BotDecisionDTO(
    long precioUnitario,
    long produccionUnidades,
    long inversionMarketing,
    long inversionRd,
    int cantidadEmpleados,
    long salarioPromedio,
    long prestamoSolicitado,
    long inversionFinanciera,
    String justificacionInterna   // log/audit, no se muestra al jugador
) {}

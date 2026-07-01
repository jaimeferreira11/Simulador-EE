package py.simulador.llm;

import java.math.BigDecimal;

public record CoachingContext(
    String equipoNombre,
    int trimestreNumero,
    long ingresos,
    long costosOperativos,
    long utilidadNeta,
    BigDecimal marketShare,
    int posicion,
    int totalEquipos,
    long precioUnitario,
    long inversionMarketing,
    long inversionCalidad,
    BigDecimal pip
) {}

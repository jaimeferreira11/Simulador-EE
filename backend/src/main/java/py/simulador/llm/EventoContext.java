package py.simulador.llm;

import java.math.BigDecimal;

public record EventoContext(
    String eventoNombre,
    String eventoDescripcion,
    String tipoEfecto,
    BigDecimal magnitud,
    short duracion,
    String severidad,
    String rubroNombre,
    int trimestreNumero
) {}

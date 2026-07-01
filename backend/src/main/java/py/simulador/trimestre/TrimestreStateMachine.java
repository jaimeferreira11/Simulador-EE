package py.simulador.trimestre;

import py.simulador.common.InvalidStateTransitionException;

import java.util.List;
import java.util.Map;

public final class TrimestreStateMachine {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ABIERTO_DECISIONES = "ABIERTO_DECISIONES";
    public static final String CERRADO_PROCESANDO = "CERRADO_PROCESANDO";
    public static final String PROCESADO = "PROCESADO";
    public static final String ANULADO = "ANULADO";

    private static final Map<String, List<String>> TRANSICIONES = Map.of(
            PENDIENTE, List.of(ABIERTO_DECISIONES, ANULADO),
            ABIERTO_DECISIONES, List.of(CERRADO_PROCESANDO, ANULADO),
            CERRADO_PROCESANDO, List.of(PROCESADO, ABIERTO_DECISIONES), // rollback on failure
            PROCESADO, List.of(),
            ANULADO, List.of()
    );

    private TrimestreStateMachine() {}

    public static void validarTransicion(String estadoActual, String estadoDestino) {
        List<String> validas = TRANSICIONES.getOrDefault(estadoActual, List.of());
        if (!validas.contains(estadoDestino)) {
            throw new InvalidStateTransitionException("trimestre", estadoActual, estadoDestino, validas);
        }
    }

    public static List<String> transicionesValidas(String estadoActual) {
        return TRANSICIONES.getOrDefault(estadoActual, List.of());
    }
}

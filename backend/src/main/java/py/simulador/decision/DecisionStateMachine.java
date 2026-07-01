package py.simulador.decision;

import py.simulador.common.InvalidStateTransitionException;

import java.util.List;
import java.util.Map;

public final class DecisionStateMachine {

    public static final String BORRADOR = "BORRADOR";
    public static final String ENVIADA = "ENVIADA";
    public static final String PROCESADA = "PROCESADA";

    private static final Map<String, List<String>> TRANSICIONES = Map.of(
            BORRADOR, List.of(BORRADOR, ENVIADA),      // BORRADOR->BORRADOR = update
            ENVIADA, List.of(BORRADOR, PROCESADA),      // ENVIADA->BORRADOR = reabrir (moderador)
            PROCESADA, List.of()                         // terminal
    );

    private DecisionStateMachine() {}

    public static void validarTransicion(String estadoActual, String estadoDestino) {
        List<String> validas = TRANSICIONES.getOrDefault(estadoActual, List.of());
        if (!validas.contains(estadoDestino)) {
            throw new InvalidStateTransitionException("decision", estadoActual, estadoDestino, validas);
        }
    }

    public static List<String> transicionesValidas(String estadoActual) {
        return TRANSICIONES.getOrDefault(estadoActual, List.of());
    }
}

package py.simulador.competencia;

import py.simulador.common.InvalidStateTransitionException;

import java.util.List;
import java.util.Map;

public final class CompetenciaStateMachine {

    public static final String BORRADOR = "BORRADOR";
    public static final String ABIERTA_INSCRIPCION = "ABIERTA_INSCRIPCION";
    public static final String EN_CURSO = "EN_CURSO";
    public static final String PAUSADA = "PAUSADA";
    public static final String PENDIENTE_FINALIZAR = "PENDIENTE_FINALIZAR";
    public static final String FINALIZADA = "FINALIZADA";
    public static final String ARCHIVADA = "ARCHIVADA";

    private static final Map<String, List<String>> TRANSICIONES = Map.ofEntries(
            Map.entry(BORRADOR, List.of(ABIERTA_INSCRIPCION, EN_CURSO, ARCHIVADA)),
            Map.entry(ABIERTA_INSCRIPCION, List.of(EN_CURSO)),
            Map.entry(EN_CURSO, List.of(PAUSADA, PENDIENTE_FINALIZAR, FINALIZADA)),
            Map.entry(PAUSADA, List.of(EN_CURSO, FINALIZADA)),
            Map.entry(PENDIENTE_FINALIZAR, List.of(FINALIZADA)),
            Map.entry(FINALIZADA, List.of(ARCHIVADA)),
            Map.entry(ARCHIVADA, List.of())
    );

    private CompetenciaStateMachine() {}

    public static void validarTransicion(String estadoActual, String estadoDestino) {
        List<String> validas = TRANSICIONES.getOrDefault(estadoActual, List.of());
        if (!validas.contains(estadoDestino)) {
            throw new InvalidStateTransitionException("competencia", estadoActual, estadoDestino, validas);
        }
    }

    public static List<String> transicionesValidas(String estadoActual) {
        return TRANSICIONES.getOrDefault(estadoActual, List.of());
    }
}

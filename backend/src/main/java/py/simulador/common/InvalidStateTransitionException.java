package py.simulador.common;

import java.util.List;

public class InvalidStateTransitionException extends RuntimeException {

    private final String estadoActual;
    private final List<String> transicionesValidas;

    public InvalidStateTransitionException(String entity, String estadoActual, String accion, List<String> transicionesValidas) {
        super("No se puede " + accion + " " + entity + " en estado " + estadoActual
                + ". Transiciones validas: " + transicionesValidas);
        this.estadoActual = estadoActual;
        this.transicionesValidas = transicionesValidas;
    }

    public String getEstadoActual() { return estadoActual; }
    public List<String> getTransicionesValidas() { return transicionesValidas; }
}

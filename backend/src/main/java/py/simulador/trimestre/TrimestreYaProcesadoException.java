package py.simulador.trimestre;

import lombok.Getter;

/**
 * Señala que se intentó cerrar un trimestre que ya está PROCESADO.
 * El controller la captura para devolver 409 con el resultado existente.
 */
@Getter
public class TrimestreYaProcesadoException extends RuntimeException {

    private final TrimestreEntity trimestre;

    public TrimestreYaProcesadoException(TrimestreEntity trimestre) {
        super("El trimestre Q" + trimestre.getNumero() + " ya fue procesado");
        this.trimestre = trimestre;
    }
}

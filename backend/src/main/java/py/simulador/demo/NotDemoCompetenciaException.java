package py.simulador.demo;

import py.simulador.common.BusinessValidationException;

/** Thrown when a demo endpoint is invoked against a non-DEMO competencia. */
public class NotDemoCompetenciaException extends BusinessValidationException {
    public NotDemoCompetenciaException(String codigo) {
        super("Operación solo aplicable a la competencia DEMO (recibido código: '" + codigo + "')");
    }
}

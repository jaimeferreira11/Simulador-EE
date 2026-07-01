package py.simulador.trimestre;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import py.simulador.common.InvalidStateTransitionException;

import static org.junit.jupiter.api.Assertions.*;
import static py.simulador.trimestre.TrimestreStateMachine.*;

class TrimestreStateMachineTest {

    @ParameterizedTest
    @CsvSource({
            "PENDIENTE,           ABIERTO_DECISIONES",
            "PENDIENTE,           ANULADO",
            "ABIERTO_DECISIONES,  CERRADO_PROCESANDO",
            "ABIERTO_DECISIONES,  ANULADO",
            "CERRADO_PROCESANDO,  PROCESADO",
            "CERRADO_PROCESANDO,  ABIERTO_DECISIONES"
    })
    void transicionesValidas(String desde, String hasta) {
        assertDoesNotThrow(() -> validarTransicion(desde, hasta));
    }

    @ParameterizedTest
    @CsvSource({
            "PENDIENTE,           PROCESADO",
            "PENDIENTE,           CERRADO_PROCESANDO",
            "ABIERTO_DECISIONES,  PENDIENTE",
            "ABIERTO_DECISIONES,  PROCESADO",
            "CERRADO_PROCESANDO,  PENDIENTE",
            "PROCESADO,           ABIERTO_DECISIONES",
            "PROCESADO,           PENDIENTE",
            "ANULADO,             PENDIENTE"
    })
    void transicionesInvalidas(String desde, String hasta) {
        assertThrows(InvalidStateTransitionException.class,
                () -> validarTransicion(desde, hasta));
    }

    @Test
    void procesadoEsTerminal() {
        assertTrue(TrimestreStateMachine.transicionesValidas(PROCESADO).isEmpty());
    }

    @Test
    void anuladoEsTerminal() {
        assertTrue(TrimestreStateMachine.transicionesValidas(ANULADO).isEmpty());
    }
}

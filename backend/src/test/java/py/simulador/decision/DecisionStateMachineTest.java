package py.simulador.decision;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import py.simulador.common.InvalidStateTransitionException;

import static org.junit.jupiter.api.Assertions.*;
import static py.simulador.decision.DecisionStateMachine.*;

class DecisionStateMachineTest {

    @ParameterizedTest
    @CsvSource({
            "BORRADOR,  BORRADOR",
            "BORRADOR,  ENVIADA",
            "ENVIADA,   BORRADOR",
            "ENVIADA,   PROCESADA"
    })
    void transicionesValidas(String desde, String hasta) {
        assertDoesNotThrow(() -> validarTransicion(desde, hasta));
    }

    @ParameterizedTest
    @CsvSource({
            "BORRADOR,  PROCESADA",
            "PROCESADA, BORRADOR",
            "PROCESADA, ENVIADA"
    })
    void transicionesInvalidas(String desde, String hasta) {
        assertThrows(InvalidStateTransitionException.class,
                () -> validarTransicion(desde, hasta));
    }

    @Test
    void procesadaEsTerminal() {
        assertTrue(DecisionStateMachine.transicionesValidas(PROCESADA).isEmpty());
    }
}

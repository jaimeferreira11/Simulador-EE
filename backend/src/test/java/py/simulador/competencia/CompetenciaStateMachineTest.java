package py.simulador.competencia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import py.simulador.common.InvalidStateTransitionException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static py.simulador.competencia.CompetenciaStateMachine.*;

class CompetenciaStateMachineTest {

    @ParameterizedTest
    @CsvSource({
            "BORRADOR,              ABIERTA_INSCRIPCION",
            "BORRADOR,              EN_CURSO",
            "BORRADOR,              ARCHIVADA",
            "ABIERTA_INSCRIPCION,   EN_CURSO",
            "EN_CURSO,              PAUSADA",
            "EN_CURSO,              PENDIENTE_FINALIZAR",
            "EN_CURSO,              FINALIZADA",
            "PAUSADA,               EN_CURSO",
            "PAUSADA,               FINALIZADA",
            "PENDIENTE_FINALIZAR,   FINALIZADA",
            "FINALIZADA,            ARCHIVADA"
    })
    void transicionesValidas(String desde, String hasta) {
        assertDoesNotThrow(() -> validarTransicion(desde, hasta));
    }

    @ParameterizedTest
    @CsvSource({
            "BORRADOR,              FINALIZADA",
            "BORRADOR,              PAUSADA",
            "ABIERTA_INSCRIPCION,   PAUSADA",
            "ABIERTA_INSCRIPCION,   FINALIZADA",
            "EN_CURSO,              BORRADOR",
            "EN_CURSO,              ARCHIVADA",
            "PAUSADA,               BORRADOR",
            "PAUSADA,               ARCHIVADA",
            "PENDIENTE_FINALIZAR,   EN_CURSO",
            "PENDIENTE_FINALIZAR,   ARCHIVADA",
            "FINALIZADA,            EN_CURSO",
            "FINALIZADA,            BORRADOR",
            "ARCHIVADA,             BORRADOR",
            "ARCHIVADA,             EN_CURSO"
    })
    void transicionesInvalidas(String desde, String hasta) {
        assertThrows(InvalidStateTransitionException.class,
                () -> validarTransicion(desde, hasta));
    }

    @Test
    void archivadaEsTerminal() {
        List<String> validas = CompetenciaStateMachine.transicionesValidas(ARCHIVADA);
        assertTrue(validas.isEmpty(), "ARCHIVADA es un estado terminal");
    }
}

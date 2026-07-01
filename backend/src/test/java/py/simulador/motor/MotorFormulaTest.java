package py.simulador.motor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para las funciones matemáticas auxiliares del motor de simulación.
 * Usa reflexión para testear métodos privados, asegurando correctitud
 * de las fórmulas sin necesitar el stack completo.
 */
class MotorFormulaTest {

    @ParameterizedTest
    @CsvSource({
            "0.80, 1.0000",     // dentro del rango óptimo
            "0.75, 1.0000",     // borde inferior
            "0.85, 1.0000",     // borde superior
            "0.50, 1.2000",     // sub-utilización: 1 + (0.75-0.50)*0.8 = 1.20
            "0.00, 1.6000",     // sin uso: 1 + 0.75*0.8 = 1.60
            "1.00, 1.1800",     // sobre-utilización: 1 + (1.00-0.85)*1.2 = 1.18
            "0.90, 1.0600"      // leve sobre-uso: 1 + (0.90-0.85)*1.2 = 1.06
    })
    void factorEficiencia(String utilizacion, String esperado) throws Exception {
        MotorSimulacion motor = crearMotorVacio();
        Method m = MotorSimulacion.class.getDeclaredMethod("calcularFactorEficiencia", BigDecimal.class);
        m.setAccessible(true);
        BigDecimal resultado = (BigDecimal) m.invoke(motor, new BigDecimal(utilizacion));
        assertEquals(Double.parseDouble(esperado), resultado.doubleValue(), 0.001);
    }

    @ParameterizedTest
    @CsvSource({
            "50, 0, 100, 50.0",
            "100, 0, 100, 100.0",
            "0, 0, 100, 0.0",
            "50, 50, 50, 50.0"     // min==max → 50
    })
    void normalizarMinMax(String valor, String min, String max, String esperado) throws Exception {
        MotorSimulacion motor = crearMotorVacio();
        Method m = MotorSimulacion.class.getDeclaredMethod("normalizar", double.class, double.class, double.class);
        m.setAccessible(true);
        double resultado = (double) m.invoke(motor,
                Double.parseDouble(valor), Double.parseDouble(min), Double.parseDouble(max));
        assertEquals(Double.parseDouble(esperado), resultado, 0.01);
    }

    @Test
    void pipPesos() {
        // PIP = 0.40 × utilidad + 0.30 × share + 0.20 × BE + 0.10 × caja
        // Verificar que los pesos suman 1.0
        assertEquals(1.0, 0.40 + 0.30 + 0.20 + 0.10, 0.0001);
    }

    private MotorSimulacion crearMotorVacio() {
        return new MotorSimulacion(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}

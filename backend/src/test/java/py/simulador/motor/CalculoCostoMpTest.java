package py.simulador.motor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests para {@link CalculoCostoMp}, la fuente única del costo unitario de MP.
 *
 * <p>Garantiza que:
 * <ul>
 *   <li>El costo unitario expuesto al jugador equivale EXACTAMENTE a
 *       {@code base × inflacionAcumulada × mixTC × factorCostoMp × factorCostoLogistico},
 *       con el mismo redondeo del motor.</li>
 *   <li>NO incluye {@code factorEficiencia} (la versión sin redondear permite al motor
 *       multiplicar por eficiencia y redondear una sola vez, manteniendo su número idéntico).</li>
 * </ul>
 */
class CalculoCostoMpTest {

    @Test
    @DisplayName("costo unitario sin eficiencia = base × inflación × mixTC × factorCostoMp × factorCostoLog")
    void costoUnitarioSinEficiencia_matchesFormula() {
        long base = 14_000L;
        BigDecimal inflacionAcumulada = new BigDecimal("1.0125");     // 1.25% acumulado
        BigDecimal tipoCambio = new BigDecimal("7035");               // TC del Q
        BigDecimal pctMpImportada = new BigDecimal("0.4000");         // 40% importada
        BigDecimal factorCostoMp = new BigDecimal("1.10");            // evento diesel +10%
        BigDecimal factorCostoLog = new BigDecimal("1.05");           // evento logístico +5%

        // Fórmula esperada, replicada paso a paso
        BigDecimal mixNacional = BigDecimal.ONE.subtract(pctMpImportada);
        BigDecimal tcNorm = tipoCambio.divide(CalculoCostoMp.TC_BASE, 6, RoundingMode.HALF_UP);
        BigDecimal mixTC = mixNacional.add(pctMpImportada.multiply(tcNorm));
        BigDecimal esperado = BigDecimal.valueOf(base)
                .multiply(inflacionAcumulada)
                .multiply(mixTC)
                .multiply(factorCostoMp)
                .multiply(factorCostoLog);

        BigDecimal actual = CalculoCostoMp.costoUnitarioSinEficiencia(
                base, inflacionAcumulada, tipoCambio, pctMpImportada, factorCostoMp, factorCostoLog);

        assertEquals(0, esperado.compareTo(actual),
                "Costo unitario sin eficiencia debe igualar la fórmula del motor");

        long esperadoRedondeado = esperado.setScale(0, RoundingMode.HALF_UP).longValue();
        long actualRedondeado = CalculoCostoMp.costoUnitarioSinEficienciaRedondeado(
                base, inflacionAcumulada, tipoCambio, pctMpImportada, factorCostoMp, factorCostoLog);
        assertEquals(esperadoRedondeado, actualRedondeado,
                "Redondeo a guaraníes (HALF_UP) consistente con el motor");
    }

    @Test
    @DisplayName("sin MP importada ni eventos: costo = base × inflación (mixTC=1)")
    void costoUnitario_sinImportadaNiEventos() {
        long base = 14_000L;
        BigDecimal inflacionAcumulada = new BigDecimal("1.02");

        long actual = CalculoCostoMp.costoUnitarioSinEficienciaRedondeado(
                base, inflacionAcumulada, new BigDecimal("6700"),
                BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE);

        // mixTC = (1-0) + 0×(6700/6700) = 1 ; costo = 14000 × 1.02 = 14280
        assertEquals(14_280L, actual);
    }

    @Test
    @DisplayName("excluye factorEficiencia: el motor reproduce el costo previo multiplicando aparte")
    void excluyeEficiencia_reproduceCostoMotor() {
        long base = 14_000L;
        BigDecimal inflacionAcumulada = new BigDecimal("1.0125");
        BigDecimal tipoCambio = new BigDecimal("7035");
        BigDecimal pctMpImportada = new BigDecimal("0.4000");
        BigDecimal factorCostoMp = new BigDecimal("1.10");
        BigDecimal factorCostoLog = new BigDecimal("1.05");
        BigDecimal factorEficiencia = new BigDecimal("1.08"); // utilización fuera del óptimo

        // Cómo lo hace el motor tras la refactorización: helper (sin redondear) × eficiencia, luego redondea
        BigDecimal sinEficiencia = CalculoCostoMp.costoUnitarioSinEficiencia(
                base, inflacionAcumulada, tipoCambio, pctMpImportada, factorCostoMp, factorCostoLog);
        long costoMotor = sinEficiencia.multiply(factorEficiencia)
                .setScale(0, RoundingMode.HALF_UP).longValue();

        // Cómo lo hacía el motor antes de la refactorización (fórmula inline completa)
        BigDecimal costoMpBase = BigDecimal.valueOf(base);
        BigDecimal mixNacional = BigDecimal.ONE.subtract(pctMpImportada);
        BigDecimal tcNorm = tipoCambio.divide(CalculoCostoMp.TC_BASE, 6, RoundingMode.HALF_UP);
        BigDecimal mixTC = mixNacional.add(pctMpImportada.multiply(tcNorm));
        long costoInlinePrevio = costoMpBase
                .multiply(inflacionAcumulada)
                .multiply(mixTC)
                .multiply(factorCostoMp)
                .multiply(factorCostoLog)
                .multiply(factorEficiencia)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        assertEquals(costoInlinePrevio, costoMotor,
                "La refactorización debe ser byte-for-byte equivalente al cálculo inline previo");

        // Y el costo expuesto al jugador (sin eficiencia) es estrictamente menor cuando eficiencia>1
        long costoJugador = CalculoCostoMp.costoUnitarioSinEficienciaRedondeado(
                base, inflacionAcumulada, tipoCambio, pctMpImportada, factorCostoMp, factorCostoLog);
        assertTrue(costoJugador < costoMotor,
                "El costo por unidad mostrado al jugador excluye la penalización de eficiencia");
    }

    @Test
    @DisplayName("magnitudEfectiva: usa magnitudAplicada cuando está presente")
    void magnitudEfectiva_usaAplicadaCuandoPresente() {
        BigDecimal aplicada = new BigDecimal("0.10");
        BigDecimal porDefecto = new BigDecimal("0.25");
        assertEquals(0, aplicada.compareTo(
                CalculoCostoMp.magnitudEfectiva(aplicada, porDefecto)),
                "Si magnitudAplicada no es null, se usa esa magnitud");
    }

    @Test
    @DisplayName("magnitudEfectiva: cae al magnitudDefault del catálogo cuando aplicada es null (como el motor)")
    void magnitudEfectiva_fallbackAlDefaultCuandoNull() {
        BigDecimal porDefecto = new BigDecimal("0.10"); // diesel +10% en catálogo
        assertEquals(0, porDefecto.compareTo(
                CalculoCostoMp.magnitudEfectiva(null, porDefecto)),
                "Con magnitudAplicada=null debe usarse magnitudDefault, igual que MotorSimulacion");
    }

    @Test
    @DisplayName("preview COSTO_MP con magnitudAplicada=null usa el default del catálogo, NO 0")
    void previewCostoMp_magnitudNull_usaDefaultNoCero() {
        long base = 14_000L;
        BigDecimal inflacionAcumulada = new BigDecimal("1.0125");
        BigDecimal tipoCambio = new BigDecimal("7035");
        BigDecimal pctMpImportada = new BigDecimal("0.4000");
        BigDecimal magnitudDefault = new BigDecimal("0.10"); // evento COSTO_MP +10%, magnitudAplicada=null

        // Cómo lo resuelve el preview (y el motor): factorCostoMp = 1 + magnitudEfectiva(null, default)
        BigDecimal magEfectiva = CalculoCostoMp.magnitudEfectiva(null, magnitudDefault);
        BigDecimal factorCostoMpCorrecto = BigDecimal.ONE.add(magEfectiva);
        long costoConDefault = CalculoCostoMp.costoUnitarioSinEficienciaRedondeado(
                base, inflacionAcumulada, tipoCambio, pctMpImportada,
                factorCostoMpCorrecto, BigDecimal.ONE);

        // El bug previo: tratar magnitud null como 0.0 → factorCostoMp = 1 + 0 = 1 (sin efecto)
        long costoBuggyComoCero = CalculoCostoMp.costoUnitarioSinEficienciaRedondeado(
                base, inflacionAcumulada, tipoCambio, pctMpImportada,
                BigDecimal.ONE, BigDecimal.ONE);

        assertTrue(costoConDefault > costoBuggyComoCero,
                "Con magnitud null el preview debe aplicar el default del catálogo (costo mayor), no 0");

        // Y debe coincidir con aplicar el factor del catálogo explícitamente
        long costoEsperado = CalculoCostoMp.costoUnitarioSinEficienciaRedondeado(
                base, inflacionAcumulada, tipoCambio, pctMpImportada,
                new BigDecimal("1.10"), BigDecimal.ONE);
        assertEquals(costoEsperado, costoConDefault,
                "El preview con magnitud null debe igualar el costo del motor con factor=1.10");
    }
}

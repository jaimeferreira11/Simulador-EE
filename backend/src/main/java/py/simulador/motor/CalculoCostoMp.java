package py.simulador.motor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single source of truth para el costo unitario de materia prima ajustado por trimestre.
 *
 * <p>El motor de simulación y el servicio de contexto de decisión comparten esta misma
 * fórmula. El costo unitario ajustado SIN el factor de eficiencia es:
 *
 * <pre>
 *   mixTC = (1 - pctMpImportada) + pctMpImportada × (tipoCambio / TC_BASE)
 *   costoUnitarioMp = costoUnitMpBase × inflacionAcumulada × mixTC
 *                     × factorCostoMp × factorCostoLogistico
 * </pre>
 *
 * <p>El motor multiplica adicionalmente por {@code factorEficiencia} (que depende de la
 * utilización de planta, es decir de la propia decisión de producción del equipo) y recién
 * entonces redondea a entero. El contexto de decisión expone el valor SIN
 * {@code factorEficiencia}: es el costo "honesto" de una unidad de materia prima este
 * trimestre, estable e independiente de la producción que el jugador esté tipeando.
 */
public final class CalculoCostoMp {

    /** Tipo de cambio base de referencia para normalizar el mix de MP importada. */
    public static final BigDecimal TC_BASE = new BigDecimal("6700");

    private CalculoCostoMp() {
    }

    /**
     * Costo unitario de MP ajustado por trimestre, SIN aplicar el factor de eficiencia,
     * devuelto como {@link BigDecimal} sin redondear. El motor multiplica este resultado
     * por {@code factorEficiencia} y redondea una sola vez, garantizando que su número final
     * sea idéntico al previo a la refactorización.
     *
     * @param costoUnitMpBase    costo unitario base de MP del rubro (guaraníes)
     * @param inflacionAcumulada factor de inflación acumulada hasta este Q (≥ 1.0)
     * @param tipoCambio         tipo de cambio del Q
     * @param pctMpImportada     porcentaje de MP importada (0.0000–1.0000)
     * @param factorCostoMp      factor de evento sobre costo de MP (1.0 si no aplica)
     * @param factorCostoLog     factor de evento sobre costo logístico (1.0 si no aplica)
     * @return costo unitario ajustado (sin eficiencia), sin redondear
     */
    public static BigDecimal costoUnitarioSinEficiencia(
            long costoUnitMpBase,
            BigDecimal inflacionAcumulada,
            BigDecimal tipoCambio,
            BigDecimal pctMpImportada,
            BigDecimal factorCostoMp,
            BigDecimal factorCostoLog) {

        BigDecimal costoMpBase = BigDecimal.valueOf(costoUnitMpBase);
        BigDecimal mixNacional = BigDecimal.ONE.subtract(pctMpImportada);
        // TC normalizado: tipoCambio actual / TC_BASE
        BigDecimal tcNorm = tipoCambio.divide(TC_BASE, 6, RoundingMode.HALF_UP);
        BigDecimal mixTC = mixNacional.add(pctMpImportada.multiply(tcNorm));

        return costoMpBase
                .multiply(inflacionAcumulada)
                .multiply(mixTC)
                .multiply(factorCostoMp)
                .multiply(factorCostoLog);
    }

    /**
     * Resuelve la magnitud efectiva de un evento EXACTAMENTE como lo hace
     * {@code MotorSimulacion}: usa {@code magnitudAplicada} si está presente; si es null,
     * cae al {@code magnitudDefault} del catálogo.
     *
     * <p>El preview de costo del contexto de decisión DEBE usar esta misma resolución para no
     * divergir del motor cuando un evento activo tiene {@code magnitudAplicada=null} (caso real
     * en los Golden Files). Tratar null como 0.0 (como hace la lista de display) produciría un
     * {@code costoUnitarioMp} de preview inconsistente con el cierre real del trimestre.
     *
     * @param magnitudAplicada magnitud específica del evento en la competencia (puede ser null)
     * @param magnitudDefault  magnitud default del catálogo (fallback; puede ser null)
     * @return la magnitud efectiva, o null si ambas son null
     */
    public static BigDecimal magnitudEfectiva(BigDecimal magnitudAplicada, BigDecimal magnitudDefault) {
        return magnitudAplicada != null ? magnitudAplicada : magnitudDefault;
    }

    /**
     * Igual que {@link #costoUnitarioSinEficiencia} pero redondeado a guaraníes enteros
     * (HALF_UP), consistente con el redondeo del motor. Usado por el contexto de decisión
     * para exponer un costo por unidad estable al jugador.
     */
    public static long costoUnitarioSinEficienciaRedondeado(
            long costoUnitMpBase,
            BigDecimal inflacionAcumulada,
            BigDecimal tipoCambio,
            BigDecimal pctMpImportada,
            BigDecimal factorCostoMp,
            BigDecimal factorCostoLog) {

        return costoUnitarioSinEficiencia(
                costoUnitMpBase, inflacionAcumulada, tipoCambio,
                pctMpImportada, factorCostoMp, factorCostoLog)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }
}

package py.simulador.motor;

import java.math.BigDecimal;

/**
 * Datos de mercado compartidos entre todos los equipos durante el procesamiento de un trimestre.
 * Se calcula una sola vez y se pasa a cada equipo.
 */
public record ContextoTrimestre(
        // Demanda total del mercado (unidades) — Paso 4
        long demandaTotalMercado,

        // Promedios del mercado para calcular competitividad — Paso 5
        long precioPromedio,
        long marketingPromedio,
        BigDecimal calidadPromedio,

        // Pesos de competitividad (pueden venir del rubro o de un evento override)
        BigDecimal pesoPrecio,
        BigDecimal pesoMarketing,
        BigDecimal pesoCalidad,
        BigDecimal pesoMarca,

        // Exponentes de elasticidad del rubro
        BigDecimal alfaPrecio,
        BigDecimal betaMarketing,
        BigDecimal gammaCalidad,

        // Factores de evento aplicados este Q
        BigDecimal factorCostoLogistico,
        BigDecimal factorCostoFijo,
        BigDecimal factorCostoMp,

        // Parámetros macro del Q
        BigDecimal inflacionAcumulada,
        BigDecimal tipoCambio,
        BigDecimal tasaTrimestral,
        BigDecimal ipsPatronal,
        BigDecimal aguinaldoFactor,
        BigDecimal tasaIre,

        // Parámetros del rubro
        long costoUnitMp,
        BigDecimal pctMpImportada,
        long costosFijosTrim,
        BigDecimal depreciacionTrim,
        long costoExpansionCapacidad,
        BigDecimal decaimientoBe,
        BigDecimal costoAlmacenamiento
) {}

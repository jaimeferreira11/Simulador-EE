package py.simulador.motor;

import java.math.BigDecimal;

/**
 * Resultado intermedio del cálculo del motor para un equipo.
 * Se construye paso a paso y luego se persiste en resultado_calculo y snapshot CIERRE.
 */
public class ResultadoEquipo {

    // Identificación
    public long equipoId;
    public long trimestreId;

    // Paso 2: Capacidad y producción
    public long capacidadDisponible;
    public long produccionReal;
    public BigDecimal utilizacionCapacidad;
    public BigDecimal factorEficiencia;

    // Paso 3: Marca y calidad
    public BigDecimal brandEquity;
    public long idAcumulado;
    public BigDecimal calidadPercibida;

    // Paso 5: Competitividad y share
    public BigDecimal competitividad;
    public BigDecimal share;

    // Paso 6: Ventas
    public long demandaAsignada;
    public long disponibleVenta;
    public long ventasUnidades;
    public long inventarioFinal;

    // Paso 7: Ingresos
    public long ingresos;

    // Paso 8: Costos
    public long costoUnitMpAjustado;
    public long costoMpTotal;
    public long salarioNuevo;
    public short headcountNuevo;
    public long costoLaboral;
    public long costoFijo;
    public long costoMarketing;
    public long costoId;
    public long costoCapacitacion;
    public long costoAlmacenamiento;
    public long depreciacion;
    public long intereses;
    public long costosOperativosTotal;

    // Paso 9: Utilidad
    public long utilidadOperativa;
    public long utilidadAntesImpuestos;
    public long impuestoIre;
    public long utilidadNeta;

    // Paso 10: Cierre
    public long cajaFinal;
    public long capacidadFinal;
    public long deudaFinal;
    public long valorPlantaFinal;
    public long patrimonioNeto;

    // Paso 11: PIP
    public BigDecimal pipTrimestre;
}

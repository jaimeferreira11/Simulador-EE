package py.simulador.resultado;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Bundle de datos pre-cargado que se pasa a {@link ReportePdfBuilder} para
 * renderizar el reporte. Mantener este DTO desacoplado de las entidades
 * permite construir el PDF en tests unitarios sin levantar el contexto de
 * Spring.
 */
public record ReporteData(
        CompetenciaInfo competencia,
        List<TrimestreInfo> trimestresProcesados,
        List<RankingItem> rankingFinal,
        Integer numUltimoTrimestreProcesado,
        String ganador,
        List<EquipoReporte> equipos,
        List<EventoReporte> eventos,
        List<DecisionReporte> decisiones,
        List<AuditoriaReporte> auditoria
) {

    public record CompetenciaInfo(
            Long id,
            String codigo,
            String nombre,
            String estado,
            int numTrimestres,
            OffsetDateTime inicioAt,
            OffsetDateTime cierreAt
    ) {}

    public record TrimestreInfo(Long id, int numero, String estado) {}

    public record RankingItem(
            int posicion,
            Long equipoId,
            String nombre,
            boolean esBot,
            BigDecimal pipAcumulado,
            long utilidadAcumulada,
            long cajaActual,
            BigDecimal shareActual
    ) {}

    public record EquipoReporte(
            Long id,
            String nombre,
            boolean esBot,
            String dificultad,
            String personalidad,
            Integer posicionFinal,
            List<TrimestreMetric> metricas
    ) {}

    public record TrimestreMetric(
            int trimestreNumero,
            Integer posicion,
            BigDecimal pip,
            long ingresos,
            long utilidadNeta,
            BigDecimal share,
            long caja
    ) {}

    public record EventoReporte(
            int trimestreNumero,
            String nombre,
            String severidad,
            String origen,
            BigDecimal magnitud,
            int duracion
    ) {}

    public record DecisionReporte(
            int trimestreNumero,
            String equipoNombre,
            long precio,
            long produccion,
            long marketing,
            long invCapacidad,
            int contrataciones,
            long invId,
            long prestamo,
            long dividendos
    ) {}

    public record AuditoriaReporte(
            OffsetDateTime fecha,
            String tipo,
            String descripcion
    ) {}
}

package py.simulador.decision;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO que agrupa todo el contexto necesario para la pantalla de decisiones v2.
 * Un unico GET reemplaza multiples llamadas del frontend.
 */
public record ContextoDecisionDTO(
        TrimestreInfo trimestre,
        @JsonProperty("snapshot_inicio") SnapshotInicioDTO snapshotInicio,
        MercadoDTO mercado,
        @JsonProperty("ranking_anterior") List<RankingItemDTO> rankingAnterior,
        @JsonProperty("eventos_activos") List<EventoActivoDTO> eventosActivos,
        @JsonProperty("decision_anterior") DecisionAnteriorDTO decisionAnterior,
        LimitesDTO limites,
        PermisosDTO permisos,
        @JsonProperty("pesos_competitividad") PesosDTO pesosCompetitividad,
        @JsonProperty("costo_unitario_mp") long costoUnitarioMp,
        ProductoRubroDTO producto
) {

    public record TrimestreInfo(
            int numero,
            String estado,
            @JsonProperty("cierre_at") String cierreAt
    ) {}

    public record SnapshotInicioDTO(
            long caja,
            long deuda,
            @JsonProperty("patrimonio_neto") long patrimonioNeto,
            long capacidad,
            int headcount,
            long salario,
            long inventario,
            @JsonProperty("brand_equity") double brandEquity,
            @JsonProperty("calidad_percibida") double calidadPercibida,
            @JsonProperty("id_acumulado") long idAcumulado,
            double pip
    ) {}

    public record MercadoDTO(
            @JsonProperty("demanda_total_estimada") long demandaTotalEstimada,
            @JsonProperty("precio_promedio") long precioPromedio,
            @JsonProperty("id_acum_promedio") long idAcumPromedio,
            @JsonProperty("marketing_promedio") long marketingPromedio,
            @JsonProperty("inflacion_acumulada") double inflacionAcumulada
    ) {}

    public record RankingItemDTO(
            int posicion,
            @JsonProperty("equipo_id") long equipoId,
            @JsonProperty("nombre_empresa") String nombreEmpresa,
            double pip,
            double share
    ) {}

    public record EventoActivoDTO(
            String nombre,
            String severidad,
            @JsonProperty("tipo_efecto") String tipoEfecto,
            double magnitud,
            @JsonProperty("duracion_restante") int duracionRestante,
            String descripcion,
            @JsonProperty("areas_impactadas") List<String> areasImpactadas,
            @JsonProperty("override_pesos") PesosDTO overridePesos
    ) {}

    public record DecisionAnteriorDTO(
            @JsonProperty("precio_venta") long precioVenta,
            @JsonProperty("inversion_marketing") long inversionMarketing,
            @JsonProperty("produccion_planificada") long produccionPlanificada,
            @JsonProperty("inversion_capacidad") long inversionCapacidad,
            @JsonProperty("inversion_id") long inversionId,
            @JsonProperty("contrataciones_netas") int contratacionesNetas,
            @JsonProperty("aumento_salarial_pct") double aumentoSalarialPct,
            @JsonProperty("inversion_capacitacion") long inversionCapacitacion,
            @JsonProperty("prestamo_solicitado") long prestamoSolicitado,
            @JsonProperty("dividendos_pagar") long dividendosPagar
    ) {}

    public record LimitesDTO(
            @JsonProperty("prestamo_maximo") long prestamoMaximo,
            @JsonProperty("dividendo_maximo") long dividendoMaximo,
            @JsonProperty("capacidad_maxima_produccion") long capacidadMaximaProduccion,
            @JsonProperty("salario_minimo_legal") long salarioMinimoLegal,
            @JsonProperty("puede_pedir_prestamo") boolean puedePedirPrestamo,
            @JsonProperty("razon_bloqueo_prestamo") String razonBloqueoPrestamo
    ) {}

    public record PermisosDTO(
            @JsonProperty("es_capitan") boolean esCapitan,
            @JsonProperty("area_asignada") String areaAsignada,
            @JsonProperty("campos_editables") List<String> camposEditables
    ) {}

    public record PesosDTO(
            double precio,
            double marketing,
            double calidad,
            double marca
    ) {}

    /**
     * Producto concreto del rubro y su Bill of Materials (BOM). Informacion
     * narrativa/READ-ONLY para el panel de Operaciones del jugador. La suma de
     * los costos unitarios del BOM equivale a {@code costoBaseUnitario}
     * (= parametro_rubro.costo_unit_mp).
     */
    public record ProductoRubroDTO(
            String nombre,
            String descripcion,
            @JsonProperty("unidad_medida") String unidadMedida,
            @JsonProperty("costo_base_unitario") long costoBaseUnitario,
            @JsonProperty("materias_primas") List<MateriaPrimaDTO> materiasPrimas
    ) {}

    public record MateriaPrimaDTO(
            String nombre,
            @JsonProperty("costo_unitario") long costoUnitario
    ) {}
}

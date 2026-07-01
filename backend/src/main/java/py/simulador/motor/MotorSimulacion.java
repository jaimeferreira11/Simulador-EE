package py.simulador.motor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import py.simulador.catalogo.*;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.competencia.CompetenciaStateMachine;
import py.simulador.decision.DecisionEquipoEntity;
import py.simulador.decision.DecisionEquipoRepository;
import py.simulador.decision.DecisionStateMachine;
import py.simulador.equipo.EquipoEntity;
import py.simulador.equipo.EquipoRepository;
import py.simulador.evento.EventoCompetenciaEntity;
import py.simulador.evento.EventoCompetenciaRepository;
import py.simulador.eventoauto.EventoAutomaticoAplicadoEntity;
import py.simulador.eventoauto.EventoAutomaticoReglaEntity;
import py.simulador.eventoauto.EventoAutomaticoService;
import py.simulador.resultado.*;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;
import py.simulador.trimestre.TrimestreStateMachine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Motor de simulación determinista.
 * Dados los mismos inputs siempre produce los mismos outputs.
 * Procesa un trimestre completo en una única transacción atómica.
 */
@Service
public class MotorSimulacion {

    private static final Logger log = LoggerFactory.getLogger(MotorSimulacion.class);
    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal CINCUENTA = new BigDecimal("50");
    private static final BigDecimal DIEZ_M = new BigDecimal("10000000");
    private static final BigDecimal CINCO_M = new BigDecimal("5000000");

    private final CompetenciaRepository competenciaRepo;
    private final EquipoRepository equipoRepo;
    private final TrimestreRepository trimestreRepo;
    private final DecisionEquipoRepository decisionRepo;
    private final SnapshotEstadoRepository snapshotRepo;
    private final ResultadoCalculoRepository resultadoRepo;
    private final RankingTrimestreRepository rankingRepo;
    private final EventoCompetenciaRepository eventoRepo;
    private final EventoCatalogoRepository eventoCatalogoRepo;
    private final ParametroMacroRepository macroRepo;
    private final ParametroRubroRepository paramRubroRepo;
    private final ParametroMacroTrimestreRepository macroTrimestreRepo;
    private final ParametroRubroTrimestreRepository rubroTrimestreRepo;
    private final EventoAutomaticoService eventoAutoService;

    public MotorSimulacion(CompetenciaRepository competenciaRepo,
                           EquipoRepository equipoRepo,
                           TrimestreRepository trimestreRepo,
                           DecisionEquipoRepository decisionRepo,
                           SnapshotEstadoRepository snapshotRepo,
                           ResultadoCalculoRepository resultadoRepo,
                           RankingTrimestreRepository rankingRepo,
                           EventoCompetenciaRepository eventoRepo,
                           EventoCatalogoRepository eventoCatalogoRepo,
                           ParametroMacroRepository macroRepo,
                           ParametroRubroRepository paramRubroRepo,
                           ParametroMacroTrimestreRepository macroTrimestreRepo,
                           ParametroRubroTrimestreRepository rubroTrimestreRepo,
                           EventoAutomaticoService eventoAutoService) {
        this.competenciaRepo = competenciaRepo;
        this.equipoRepo = equipoRepo;
        this.trimestreRepo = trimestreRepo;
        this.decisionRepo = decisionRepo;
        this.snapshotRepo = snapshotRepo;
        this.resultadoRepo = resultadoRepo;
        this.rankingRepo = rankingRepo;
        this.eventoRepo = eventoRepo;
        this.eventoCatalogoRepo = eventoCatalogoRepo;
        this.macroRepo = macroRepo;
        this.paramRubroRepo = paramRubroRepo;
        this.macroTrimestreRepo = macroTrimestreRepo;
        this.rubroTrimestreRepo = rubroTrimestreRepo;
        this.eventoAutoService = eventoAutoService;
    }

    /**
     * Procesa un trimestre completo. Si falla, la transacción hace rollback
     * y el trimestre debe volver a ABIERTO_DECISIONES en el caller.
     */
    /**
     * Ejecuta el procesamiento. Debe invocarse dentro de una transacción abierta
     * por el caller (TrimestreService.cerrar). No declara @Transactional propio.
     */
    public void procesarTrimestre(Long trimestreId) {
        TrimestreEntity trimestre = trimestreRepo.findById(trimestreId)
                .orElseThrow(() -> new IllegalStateException("Trimestre no encontrado: " + trimestreId));
        CompetenciaEntity competencia = competenciaRepo.findById(trimestre.getCompetenciaId())
                .orElseThrow(() -> new IllegalStateException("Competencia no encontrada"));

        log.info("Procesando trimestre Q{} de competencia {} (id={})",
                trimestre.getNumero(), competencia.getCodigo(), competencia.getId());

        List<EquipoEntity> allEquipos = equipoRepo.findByCompetenciaId(competencia.getId());

        // Filtrar equipos en bancarrota: no participan del procesamiento
        List<EquipoEntity> equipos = allEquipos.stream()
                .filter(e -> !e.isEnBancarrota())
                .toList();
        ParametroRubroEntity paramRubro = paramRubroRepo.findById(competencia.getParametroRubroId())
                .orElseThrow(() -> new IllegalStateException("Parámetro rubro no encontrado"));
        ParametroMacroEntity paramMacro = macroRepo.findById(competencia.getParametroMacroId())
                .orElseThrow(() -> new IllegalStateException("Parámetro macro no encontrado"));

        // Load quarterly params from normalized tables
        List<ParametroMacroTrimestreEntity> macroTrimestres =
                macroTrimestreRepo.findByMacroId(paramMacro.getId());
        List<ParametroRubroTrimestreEntity> rubroTrimestres =
                rubroTrimestreRepo.findByRubroParamId(paramRubro.getId());

        // --- Paso 0: Cargar snapshots INICIO de cada equipo ---
        Map<Long, SnapshotEstadoEntity> snapshots = new HashMap<>();
        for (EquipoEntity equipo : equipos) {
            SnapshotEstadoEntity snap = snapshotRepo
                    .findByEquipoIdAndTrimestreIdAndMomento(equipo.getId(), trimestreId, "INICIO")
                    .orElseThrow(() -> new IllegalStateException(
                            "Snapshot INICIO no encontrado para equipo " + equipo.getId()));
            snapshots.put(equipo.getId(), snap);
        }

        // --- Paso 1: Cargar decisiones, promover borradores a ENVIADA ---
        Map<Long, DecisionEquipoEntity> decisiones = cargarYPromoverDecisiones(
                equipos, trimestreId, paramRubro.getPrecioReferencia());

        // --- Construir contexto del trimestre (eventos, macro, promedios) ---
        ContextoTrimestre ctx = construirContexto(
                competencia, trimestre, paramRubro, paramMacro,
                macroTrimestres, rubroTrimestres,
                equipos, decisiones, snapshots);

        // --- Paso 1b: Apply active auto-events per team ---
        Map<Long, AutoEventEffects> autoEffectsPerTeam = new HashMap<>();
        for (EquipoEntity equipo : equipos) {
            AutoEventEffects effects = calcularEfectosAutoEventos(
                    competencia.getId(), equipo.getId(), trimestre.getNumero());
            autoEffectsPerTeam.put(equipo.getId(), effects);
        }

        // --- Pasos 2-10: Calcular resultado por equipo ---
        List<ResultadoEquipo> resultados = new ArrayList<>();
        for (EquipoEntity equipo : equipos) {
            SnapshotEstadoEntity snap = snapshots.get(equipo.getId());
            DecisionEquipoEntity dec = decisiones.get(equipo.getId());
            ResultadoEquipo r = calcularEquipo(equipo.getId(), trimestreId, snap, dec, ctx);
            resultados.add(r);
        }

        // --- Paso 5b: Calcular competitividad y share (requiere datos de todos) ---
        calcularCompetitividadYShare(resultados, ctx);

        // --- Pasos 6-10: Completar ventas, costos, utilidad, caja (dependen del share) ---
        for (ResultadoEquipo r : resultados) {
            SnapshotEstadoEntity snap = snapshots.get(r.equipoId);
            DecisionEquipoEntity dec = decisiones.get(r.equipoId);
            // Apply auto-event effects to the result
            AutoEventEffects autoFx = autoEffectsPerTeam.get(r.equipoId);
            completarCalculo(r, snap, dec, ctx, autoFx);
        }

        // --- Paso 11: Calcular PIP con normalización entre equipos ---
        calcularPIP(resultados);

        // --- Bancarrota: marcar equipos con caja negativa ---
        if (competencia.isBancarrotaHabilitada()) {
            for (ResultadoEquipo r : resultados) {
                if (r.cajaFinal < 0) {
                    EquipoEntity equipo = equipos.stream()
                            .filter(e -> e.getId().equals(r.equipoId))
                            .findFirst().orElse(null);
                    if (equipo != null && !equipo.isEnBancarrota()) {
                        equipo.setEnBancarrota(true);
                        equipo.setTrimestreBancarrota((int) trimestre.getNumero());
                        equipoRepo.save(equipo);
                        log.info("Equipo {} entro en bancarrota en Q{}",
                                equipo.getNombreEmpresa(), trimestre.getNumero());
                    }
                }
            }
        }

        // --- Evaluate auto-event rules for next quarter ---
        long sumMkt = 0;
        for (DecisionEquipoEntity dec : decisiones.values()) sumMkt += dec.getInversionMarketing();
        long mktPromedio = equipos.isEmpty() ? 1 : Math.max(1, sumMkt / equipos.size());

        for (ResultadoEquipo r : resultados) {
            DecisionEquipoEntity dec = decisiones.get(r.equipoId);
            SnapshotEstadoEntity snap = snapshots.get(r.equipoId);
            eventoAutoService.evaluar(competencia.getId(), r.equipoId,
                    trimestre.getNumero(), competencia.getNumTrimestres(),
                    dec, r, snap, mktPromedio);
        }

        // --- Persistir resultado_calculo y snapshot CIERRE ---
        for (ResultadoEquipo r : resultados) {
            persistirResultado(r, ctx);
            persistirSnapshotCierre(r, snapshots.get(r.equipoId), decisiones.get(r.equipoId));
        }

        // --- Calcular y persistir ranking ---
        persistirRanking(competencia.getId(), trimestreId, resultados);

        // --- Si es último trimestre: finalizar competencia ---
        if (trimestre.getNumero() == competencia.getNumTrimestres()) {
            finalizarCompetencia(competencia, trimestreId, resultados, equipos);
        } else {
            // Crear snapshot INICIO del Q+1 = copia del CIERRE de este Q
            crearSnapshotsInicioSiguiente(competencia.getId(), trimestre, resultados,
                    snapshots, decisiones);
        }

        // --- Marcar trimestre como PROCESADO ---
        trimestre.setEstado(TrimestreStateMachine.PROCESADO);
        trimestre.setProcesadoAt(OffsetDateTime.now());
        trimestreRepo.save(trimestre);

        // Marcar decisiones como PROCESADA
        for (DecisionEquipoEntity dec : decisiones.values()) {
            dec.setEstado(DecisionStateMachine.PROCESADA);
            decisionRepo.save(dec);
        }

        log.info("Trimestre Q{} procesado exitosamente", trimestre.getNumero());
    }

    // ====================================================================
    // PASO 1: Cargar decisiones, promover borradores, crear defaults
    // ====================================================================

    private Map<Long, DecisionEquipoEntity> cargarYPromoverDecisiones(
            List<EquipoEntity> equipos, Long trimestreId, long precioReferencia) {

        Map<Long, DecisionEquipoEntity> result = new HashMap<>();

        for (EquipoEntity equipo : equipos) {
            Optional<DecisionEquipoEntity> opt = decisionRepo.findByEquipoIdAndTrimestreId(
                    equipo.getId(), trimestreId);

            DecisionEquipoEntity dec;
            if (opt.isPresent()) {
                dec = opt.get();
                // Promover borradores a ENVIADA automáticamente al cerrar
                if (DecisionStateMachine.BORRADOR.equals(dec.getEstado())) {
                    dec.setEstado(DecisionStateMachine.ENVIADA);
                    dec.setSubmittedAt(OffsetDateTime.now());
                    dec = decisionRepo.save(dec);
                    log.debug("Borrador promovido a ENVIADA para equipo {}", equipo.getId());
                }
            } else {
                // Equipo sin decisión: crear default con precio = precio referencia, resto 0
                dec = new DecisionEquipoEntity();
                dec.setEquipoId(equipo.getId());
                dec.setTrimestreId(trimestreId);
                dec.setPrecioVenta(precioReferencia);
                dec.setProduccionPlanificada(0);
                dec.setInversionMarketing(0);
                dec.setInversionId(0);
                dec.setInversionCapacidad(0);
                dec.setInversionCapacitacion(0);
                dec.setPrestamoSolicitado(0);
                dec.setDividendosPagar(0);
                dec.setContratacionesNetas((short) 0);
                dec.setAumentoSalarialPct(BigDecimal.ZERO);
                dec.setEstado(DecisionStateMachine.ENVIADA);
                dec.setSubmittedAt(OffsetDateTime.now());
                dec = decisionRepo.save(dec);
                log.debug("Decisión default creada para equipo {}", equipo.getId());
            }
            result.put(equipo.getId(), dec);
        }
        return result;
    }

    // ====================================================================
    // Construir contexto del trimestre (macro, rubro, eventos, promedios)
    // ====================================================================

    private ContextoTrimestre construirContexto(
            CompetenciaEntity competencia, TrimestreEntity trimestre,
            ParametroRubroEntity paramRubro, ParametroMacroEntity paramMacro,
            List<ParametroMacroTrimestreEntity> macroTrimestres,
            List<ParametroRubroTrimestreEntity> rubroTrimestres,
            List<EquipoEntity> equipos,
            Map<Long, DecisionEquipoEntity> decisiones,
            Map<Long, SnapshotEstadoEntity> snapshots) {

        int q = trimestre.getNumero();

        // Inflación acumulada desde Q1 hasta este Q
        BigDecimal inflAcum = calcularInflacionAcumulada(macroTrimestres, q);

        // Tipo de cambio y tasa del Q actual (cyclic fallback for Q > stored)
        BigDecimal tipoCambio = obtenerValorMacroQ(macroTrimestres, q, ParametroMacroTrimestreEntity::getTipoCambio);
        BigDecimal tpmAnual = obtenerValorMacroQ(macroTrimestres, q, ParametroMacroTrimestreEntity::getTpmAnual);
        // Tasa activa trimestral = (TPM + spread del rubro) / 4
        BigDecimal tasaTrim = tpmAnual.add(paramRubro.getSpreadTasa())
                .divide(new BigDecimal("4"), 8, RoundingMode.HALF_UP);

        // Estacionalidad del Q (viene del rubro, ej: Q4=1.18 para retail)
        BigDecimal estacionalidad = obtenerEstacionalidad(rubroTrimestres, q);

        // Eventos activos: factores de ajuste
        BigDecimal factorDemanda = BigDecimal.ONE;
        BigDecimal factorCostoLog = BigDecimal.ONE;
        BigDecimal factorCostoFijo = BigDecimal.ONE;
        BigDecimal factorCostoMp = BigDecimal.ONE;
        // Pesos override por evento (si hay)
        BigDecimal overPrecio = null, overMkt = null, overCal = null, overMarca = null;
        int severidadMax = -1;

        // Buscar eventos activos: directos de este Q + eventos de Qs previos cuya duración cubre este Q
        List<EventoCompetenciaEntity> eventos = eventoRepo.findActivosParaTrimestre(
                competencia.getId(), trimestre.getId());
        for (EventoCompetenciaEntity ev : eventos) {
            EventoCatalogoEntity cat = eventoCatalogoRepo.findById(ev.getEventoCatalogoId())
                    .orElse(null);
            if (cat == null) continue;

            BigDecimal mag = ev.getMagnitudAplicada() != null
                    ? ev.getMagnitudAplicada() : cat.getMagnitudDefault();

            // Aplicar efecto según tipo_efecto del catálogo
            String tipo = cat.getTipoEfecto();
            if (tipo != null) {
                switch (tipo) {
                    case "DEMANDA_TOTAL" -> factorDemanda = factorDemanda.add(mag);
                    case "COSTO_LOGISTICO" -> factorCostoLog = factorCostoLog.add(mag);
                    case "COSTO_FIJO" -> factorCostoFijo = factorCostoFijo.add(mag);
                    case "COSTO_MP" -> factorCostoMp = factorCostoMp.add(mag);
                }
            }

            // Override de pesos por evento de mayor severidad
            if (cat.getOverridePesoPrecio() != null) {
                int sev = severidadOrden(cat.getSeveridad());
                if (sev > severidadMax) {
                    severidadMax = sev;
                    overPrecio = cat.getOverridePesoPrecio();
                    overMkt = cat.getOverridePesoMarketing();
                    overCal = cat.getOverridePesoCalidad();
                    overMarca = cat.getOverridePesoMarca();
                }
            }
        }

        // Pesos finales: override del evento o default del rubro
        BigDecimal pesoPrecio = overPrecio != null ? overPrecio : paramRubro.getPesoPrecio();
        BigDecimal pesoMarketing = overMkt != null ? overMkt : paramRubro.getPesoMarketing();
        BigDecimal pesoCalidad = overCal != null ? overCal : paramRubro.getPesoCalidad();
        BigDecimal pesoMarca = overMarca != null ? overMarca : paramRubro.getPesoMarca();

        // Demanda total = base × estacionalidad × factorEventoDemanda × factorMarketingAgregado
        // Factor marketing agregado: 1.05 fijo (5% estimulo por marketing de la industria)
        BigDecimal factorMktAgregado = new BigDecimal("1.05");
        long demandaTotal = BigDecimal.valueOf(paramRubro.getDemandaBaseTrim())
                .multiply(estacionalidad)
                .multiply(factorDemanda)
                .multiply(factorMktAgregado)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        // Promedios de mercado para competitividad
        long sumPrecios = 0, sumMkt = 0;
        BigDecimal sumCalidad = BigDecimal.ZERO;
        int count = equipos.size();
        for (EquipoEntity eq : equipos) {
            DecisionEquipoEntity d = decisiones.get(eq.getId());
            SnapshotEstadoEntity s = snapshots.get(eq.getId());
            sumPrecios += d.getPrecioVenta();
            sumMkt += d.getInversionMarketing();

            // Calidad se calcula aquí (Paso 3) para tener el promedio
            long idAcum = (long) (s.getIdAcumulado() * 0.85) + d.getInversionId();
            BigDecimal cal = CINCUENTA.add(BigDecimal.valueOf(idAcum).divide(CINCO_M, 4, RoundingMode.HALF_UP));
            if (cal.compareTo(CIEN) > 0) cal = CIEN;
            sumCalidad = sumCalidad.add(cal);
        }
        long precioPromedio = sumPrecios / count;
        long marketingPromedio = Math.max(1, sumMkt / count);
        BigDecimal calidadPromedio = sumCalidad.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);

        // Costo de almacenamiento: 2% del valor del inventario por Q
        BigDecimal costoAlm = new BigDecimal("0.02");

        return new ContextoTrimestre(
                demandaTotal, precioPromedio, marketingPromedio, calidadPromedio,
                pesoPrecio, pesoMarketing, pesoCalidad, pesoMarca,
                paramRubro.getElasticidadPrecio(), paramRubro.getElasticidadMarketing(),
                paramRubro.getElasticidadCalidad(),
                factorCostoLog, factorCostoFijo, factorCostoMp,
                inflAcum, tipoCambio, tasaTrim,
                paramMacro.getIpsPatronal(), paramMacro.getAguinaldoFactor(), paramMacro.getTasaIre(),
                paramRubro.getCostoUnitMp(), paramRubro.getPctMpImportada(),
                paramRubro.getCostosFijosTrim(), paramRubro.getDepreciacionTrim(),
                paramRubro.getCostoExpansionCapacidad(), paramRubro.getDecaimientoBe(),
                costoAlm
        );
    }

    // ====================================================================
    // PASOS 2-3: Capacidad, producción, brand equity, calidad (por equipo)
    // ====================================================================

    private ResultadoEquipo calcularEquipo(long equipoId, long trimestreId,
                                           SnapshotEstadoEntity snap,
                                           DecisionEquipoEntity dec,
                                           ContextoTrimestre ctx) {
        ResultadoEquipo r = new ResultadoEquipo();
        r.equipoId = equipoId;
        r.trimestreId = trimestreId;

        // --- Paso 2: Capacidad y producción ---
        // La inversión de este Q se efectiviza en Q+1 (lag=1), capacidad = la del snapshot
        r.capacidadDisponible = snap.getCapacidad();
        r.produccionReal = Math.min(dec.getProduccionPlanificada(), r.capacidadDisponible);

        // NOTE: capacidad disponible se ajusta por auto-eventos en completarCalculo()
        // después de calcular competitividad y share (ver factorCapacidad).

        if (r.capacidadDisponible > 0) {
            r.utilizacionCapacidad = BigDecimal.valueOf(r.produccionReal)
                    .divide(BigDecimal.valueOf(r.capacidadDisponible), 4, RoundingMode.HALF_UP);
        } else {
            r.utilizacionCapacidad = BigDecimal.ZERO;
        }

        // Factor eficiencia: óptimo 75-85%, penalización fuera del rango
        r.factorEficiencia = calcularFactorEficiencia(r.utilizacionCapacidad);

        // --- Paso 3: Brand Equity y Calidad ---
        BigDecimal beInicio = snap.getBrandEquity();
        if (dec.getInversionMarketing() == 0) {
            // Sin marketing: decay del BE
            beInicio = beInicio.multiply(BigDecimal.ONE.subtract(ctx.decaimientoBe()));
        }
        // Aporte del marketing: √(marketing / 10M)
        BigDecimal aporteMkt = sqrt(BigDecimal.valueOf(dec.getInversionMarketing()).divide(DIEZ_M, 8, RoundingMode.HALF_UP));
        r.brandEquity = beInicio.add(aporteMkt).min(CIEN).setScale(2, RoundingMode.HALF_UP);

        // I+D acumulado: decay 15% + nueva inversión
        r.idAcumulado = (long) (snap.getIdAcumulado() * 0.85) + dec.getInversionId();

        // Calidad percibida: 50 + idAcumulado / 5M, max 100
        r.calidadPercibida = CINCUENTA.add(
                        BigDecimal.valueOf(r.idAcumulado).divide(CINCO_M, 4, RoundingMode.HALF_UP))
                .min(CIEN).setScale(2, RoundingMode.HALF_UP);

        return r;
    }

    // ====================================================================
    // PASO 5: Competitividad y Share (requiere datos de todos los equipos)
    // ====================================================================

    private void calcularCompetitividadYShare(List<ResultadoEquipo> resultados,
                                               ContextoTrimestre ctx) {
        BigDecimal sumaCompetitividad = BigDecimal.ZERO;

        for (ResultadoEquipo r : resultados) {
            // Score precio: (precioProm / precio_i) ^ α — precio bajo = score alto
            DecisionEquipoEntity dec = decisionRepo.findByEquipoIdAndTrimestreId(
                    r.equipoId, r.trimestreId).orElseThrow();
            BigDecimal scorePrecio = pow(
                    BigDecimal.valueOf(ctx.precioPromedio())
                            .divide(BigDecimal.valueOf(dec.getPrecioVenta()), 8, RoundingMode.HALF_UP),
                    ctx.alfaPrecio());

            // Score marketing: (mkt_i / mktProm) ^ β — rendimientos decrecientes
            BigDecimal scoreMarketing = pow(
                    BigDecimal.valueOf(dec.getInversionMarketing())
                            .divide(BigDecimal.valueOf(ctx.marketingPromedio()), 8, RoundingMode.HALF_UP),
                    ctx.betaMarketing());

            // Score calidad: (calidad_i / calidadProm) ^ γ
            BigDecimal scoreCalidad = pow(
                    r.calidadPercibida.divide(ctx.calidadPromedio(), 8, RoundingMode.HALF_UP),
                    ctx.gammaCalidad());

            // Score marca: BE / 50 (normalizado a 1 = neutro)
            BigDecimal scoreMarca = r.brandEquity.divide(CINCUENTA, 8, RoundingMode.HALF_UP);

            // Competitividad = suma ponderada
            r.competitividad = ctx.pesoPrecio().multiply(scorePrecio)
                    .add(ctx.pesoMarketing().multiply(scoreMarketing))
                    .add(ctx.pesoCalidad().multiply(scoreCalidad))
                    .add(ctx.pesoMarca().multiply(scoreMarca));

            sumaCompetitividad = sumaCompetitividad.add(r.competitividad);
        }

        // Share = competitividad_i / Σ competitividad
        for (ResultadoEquipo r : resultados) {
            if (sumaCompetitividad.compareTo(BigDecimal.ZERO) > 0) {
                r.share = r.competitividad.divide(sumaCompetitividad, 6, RoundingMode.HALF_UP);
            } else {
                r.share = BigDecimal.ONE.divide(BigDecimal.valueOf(resultados.size()), 6, RoundingMode.HALF_UP);
            }
        }
    }

    // ====================================================================
    // PASOS 6-10: Ventas, ingresos, costos, utilidad, caja
    // ====================================================================

    private void completarCalculo(ResultadoEquipo r, SnapshotEstadoEntity snap,
                                   DecisionEquipoEntity dec, ContextoTrimestre ctx,
                                   AutoEventEffects autoFx) {

        // --- Apply auto-event capacity factor (reduces effective capacity this quarter) ---
        if (autoFx.factorCapacidad().compareTo(BigDecimal.ONE) != 0) {
            r.capacidadDisponible = BigDecimal.valueOf(r.capacidadDisponible)
                    .multiply(autoFx.factorCapacidad())
                    .setScale(0, RoundingMode.HALF_UP).longValue();
            if (r.capacidadDisponible < 0) r.capacidadDisponible = 0;
            // Re-cap production to adjusted capacity
            r.produccionReal = Math.min(r.produccionReal, r.capacidadDisponible);
        }

        // --- Apply auto-event production factor ---
        if (autoFx.factorProduccion().compareTo(BigDecimal.ONE) != 0) {
            r.produccionReal = BigDecimal.valueOf(r.produccionReal)
                    .multiply(autoFx.factorProduccion())
                    .setScale(0, RoundingMode.HALF_UP).longValue();
            if (r.produccionReal < 0) r.produccionReal = 0;
        }

        // --- Paso 6: Ventas ---
        // Apply auto-event demand factor (team-specific demand adjustment)
        long demandaBase = BigDecimal.valueOf(ctx.demandaTotalMercado())
                .multiply(r.share).setScale(0, RoundingMode.HALF_UP).longValue();
        r.demandaAsignada = BigDecimal.valueOf(demandaBase)
                .multiply(autoFx.factorDemanda())
                .setScale(0, RoundingMode.HALF_UP).longValue();
        r.disponibleVenta = snap.getInventario() + r.produccionReal;
        r.ventasUnidades = Math.min(r.demandaAsignada, r.disponibleVenta);
        r.inventarioFinal = r.disponibleVenta - r.ventasUnidades;

        // --- Paso 7: Ingresos ---
        r.ingresos = dec.getPrecioVenta() * r.ventasUnidades;

        // --- Paso 8: Costos ---
        // Costo unitario MP ajustado: base × inflación × mix TC × factor evento × eficiencia.
        // La parte SIN eficiencia es la fuente única (CalculoCostoMp), compartida con el
        // contexto de decisión del jugador; el motor multiplica por factorEficiencia y
        // redondea una sola vez al final (resultado idéntico al previo a la refactorización).
        BigDecimal costoUnitSinEficiencia = CalculoCostoMp.costoUnitarioSinEficiencia(
                ctx.costoUnitMp(),
                ctx.inflacionAcumulada(),
                ctx.tipoCambio(),
                ctx.pctMpImportada(),
                ctx.factorCostoMp(),
                ctx.factorCostoLogistico());
        BigDecimal costoUnitAjustado = costoUnitSinEficiencia.multiply(r.factorEficiencia);
        r.costoUnitMpAjustado = costoUnitAjustado.setScale(0, RoundingMode.HALF_UP).longValue();
        r.costoMpTotal = r.costoUnitMpAjustado * r.produccionReal;

        // Salario nuevo = salario anterior × (1 + aumento%)
        BigDecimal salarioAnterior = BigDecimal.valueOf(snap.getSalario());
        BigDecimal aumento = dec.getAumentoSalarialPct() != null ? dec.getAumentoSalarialPct() : BigDecimal.ZERO;
        r.salarioNuevo = salarioAnterior.multiply(BigDecimal.ONE.add(aumento))
                .setScale(0, RoundingMode.HALF_UP).longValue();

        // Headcount nuevo = headcount inicio + contrataciones netas
        r.headcountNuevo = (short) (snap.getHeadcount() + dec.getContratacionesNetas());

        // Costo laboral = salario × headcount × 3 meses × (1 + IPS 16.5%) + aguinaldo prorrateado
        // Fórmula del Golden File: sal × hc × 3 × (1 + IPS) + sal × hc × aguinaldo × 3
        long salXhcX3 = r.salarioNuevo * r.headcountNuevo * 3;
        BigDecimal costoLaboralBD = BigDecimal.valueOf(salXhcX3)
                .multiply(BigDecimal.ONE.add(ctx.ipsPatronal()))
                .add(BigDecimal.valueOf(salXhcX3).multiply(ctx.aguinaldoFactor()));
        r.costoLaboral = costoLaboralBD.setScale(0, RoundingMode.HALF_UP).longValue();

        // Costos fijos = base × inflación acumulada × factor evento × auto-event factor
        r.costoFijo = BigDecimal.valueOf(ctx.costosFijosTrim())
                .multiply(ctx.inflacionAcumulada())
                .multiply(ctx.factorCostoFijo())
                .multiply(autoFx.factorCostoFijo())
                .setScale(0, RoundingMode.HALF_UP).longValue();

        // Costos directos (pasan tal cual de la decisión)
        r.costoMarketing = dec.getInversionMarketing();
        r.costoId = dec.getInversionId();
        r.costoCapacitacion = dec.getInversionCapacitacion();

        // Almacenamiento = inventario final × costo unit MP × 2%
        r.costoAlmacenamiento = BigDecimal.valueOf(r.inventarioFinal)
                .multiply(BigDecimal.valueOf(r.costoUnitMpAjustado))
                .multiply(ctx.costoAlmacenamiento())
                .setScale(0, RoundingMode.HALF_UP).longValue();

        // Depreciación = valor planta × 5% trimestral
        r.depreciacion = BigDecimal.valueOf(snap.getValorPlanta())
                .multiply(ctx.depreciacionTrim())
                .setScale(0, RoundingMode.HALF_UP).longValue();

        // Intereses = deuda inicio × tasa trimestral
        r.intereses = BigDecimal.valueOf(snap.getDeuda())
                .multiply(ctx.tasaTrimestral())
                .setScale(0, RoundingMode.HALF_UP).longValue();

        // --- Paso 9: Utilidad ---
        r.costosOperativosTotal = r.costoMpTotal + r.costoLaboral + r.costoFijo
                + r.costoMarketing + r.costoId + r.costoCapacitacion
                + r.costoAlmacenamiento + r.depreciacion;
        r.utilidadOperativa = r.ingresos - r.costosOperativosTotal;
        r.utilidadAntesImpuestos = r.utilidadOperativa - r.intereses;

        // IRE = max(0, UAI × 10%), 0 si hay pérdida
        if (r.utilidadAntesImpuestos > 0) {
            r.impuestoIre = BigDecimal.valueOf(r.utilidadAntesImpuestos)
                    .multiply(ctx.tasaIre())
                    .setScale(0, RoundingMode.HALF_UP).longValue();
        } else {
            r.impuestoIre = 0;
        }
        r.utilidadNeta = r.utilidadAntesImpuestos - r.impuestoIre;

        // --- Paso 10: Cierre — caja y estado final ---
        // Caja = caja_ini + utilidadNeta + depreciación + préstamo - invCapacidad - dividendos
        r.cajaFinal = snap.getCaja() + r.utilidadNeta + r.depreciacion
                + dec.getPrestamoSolicitado() - dec.getInversionCapacidad()
                - dec.getDividendosPagar();

        // Capacidad final = capacidad inicio + invCapacidad / costoExpansión (efectiva en Q+1)
        long unidadesNuevas = ctx.costoExpansionCapacidad() > 0
                ? dec.getInversionCapacidad() / ctx.costoExpansionCapacidad() : 0;
        r.capacidadFinal = snap.getCapacidad() + unidadesNuevas;

        // Deuda final = deuda inicio + nuevo préstamo (sin amortización en MVP)
        r.deudaFinal = snap.getDeuda() + dec.getPrestamoSolicitado();

        // Valor planta = anterior - depreciación + inversión capacidad
        r.valorPlantaFinal = snap.getValorPlanta() - r.depreciacion + dec.getInversionCapacidad();

        // Patrimonio neto = caja + inventario valorizado + planta - deuda
        long inventarioValorizado = r.inventarioFinal * r.costoUnitMpAjustado;
        r.patrimonioNeto = r.cajaFinal + inventarioValorizado + r.valorPlantaFinal - r.deudaFinal;
    }

    // ====================================================================
    // PASO 11: PIP — normalización min-max entre equipos
    // ====================================================================

    private void calcularPIP(List<ResultadoEquipo> resultados) {
        // Extraer valores para normalización
        long[] utilidades = resultados.stream().mapToLong(r -> r.utilidadNeta).toArray();
        double[] shares = resultados.stream().mapToDouble(r -> r.share.doubleValue()).toArray();
        double[] bes = resultados.stream().mapToDouble(r -> r.brandEquity.doubleValue()).toArray();
        long[] cajas = resultados.stream().mapToLong(r -> r.cajaFinal).toArray();

        for (ResultadoEquipo r : resultados) {
            // Normalizar 0-100 con min-max (si todos iguales → 50)
            double scoreUtilidad = normalizar(r.utilidadNeta, min(utilidades), max(utilidades));
            double scoreShare = normalizar(r.share.doubleValue(), min(shares), max(shares));
            double scoreBE = r.brandEquity.doubleValue(); // Ya está en escala 0-100
            double scoreCaja = normalizar(r.cajaFinal, min(cajas), max(cajas));

            // PIP = 0.40 × utilidad + 0.30 × share + 0.20 × BE + 0.10 × caja
            double pip = 0.40 * scoreUtilidad + 0.30 * scoreShare + 0.20 * scoreBE + 0.10 * scoreCaja;
            r.pipTrimestre = BigDecimal.valueOf(pip).setScale(2, RoundingMode.HALF_UP);
        }
    }

    // ====================================================================
    // Persistencia
    // ====================================================================

    private void persistirResultado(ResultadoEquipo r, ContextoTrimestre ctx) {
        ResultadoCalculoEntity rc = new ResultadoCalculoEntity();
        rc.setEquipoId(r.equipoId);
        rc.setTrimestreId(r.trimestreId);

        rc.setUtilizacionCapacidad(r.utilizacionCapacidad);
        rc.setFactorEficiencia(r.factorEficiencia);
        rc.setProduccionReal(r.produccionReal);

        rc.setDemandaTotalMercado(ctx.demandaTotalMercado());
        rc.setDemandaAsignada(r.demandaAsignada);
        rc.setCompetitividad(r.competitividad);
        rc.setShare(r.share);
        rc.setVentasUnidades(r.ventasUnidades);

        rc.setIngresos(r.ingresos);

        rc.setCostoMpTotal(r.costoMpTotal);
        rc.setCostoLaboral(r.costoLaboral);
        rc.setCostoFijo(r.costoFijo);
        rc.setCostoMarketing(r.costoMarketing);
        rc.setCostoId(r.costoId);
        rc.setCostoCapacitacion(r.costoCapacitacion);
        rc.setCostoAlmacenamiento(r.costoAlmacenamiento);
        rc.setDepreciacion(r.depreciacion);
        rc.setIntereses(r.intereses);
        rc.setCostosOperativosTotal(r.costosOperativosTotal);

        rc.setUtilidadOperativa(r.utilidadOperativa);
        rc.setUtilidadAntesImpuestos(r.utilidadAntesImpuestos);
        rc.setImpuestoIre(r.impuestoIre);
        rc.setUtilidadNeta(r.utilidadNeta);
        rc.setPipTrimestre(r.pipTrimestre);

        rc.setCalculadoAt(OffsetDateTime.now());
        resultadoRepo.save(rc);
    }

    private void persistirSnapshotCierre(ResultadoEquipo r, SnapshotEstadoEntity snapInicio,
                                          DecisionEquipoEntity dec) {
        SnapshotEstadoEntity cierre = new SnapshotEstadoEntity();
        cierre.setEquipoId(r.equipoId);
        cierre.setTrimestreId(r.trimestreId);
        cierre.setMomento("CIERRE");

        cierre.setCaja(r.cajaFinal);
        cierre.setDeuda(r.deudaFinal);
        cierre.setPatrimonioNeto(r.patrimonioNeto);
        cierre.setValorPlanta(r.valorPlantaFinal);

        cierre.setCapacidad(r.capacidadFinal);
        cierre.setHeadcount(r.headcountNuevo);
        cierre.setSalario(r.salarioNuevo);
        cierre.setInventario(r.inventarioFinal);

        cierre.setBrandEquity(r.brandEquity);
        cierre.setCalidadPercibida(r.calidadPercibida);
        cierre.setIdAcumulado(r.idAcumulado);

        cierre.setPip(r.pipTrimestre);

        snapshotRepo.save(cierre);
    }

    private void persistirRanking(Long competenciaId, Long trimestreId,
                                   List<ResultadoEquipo> resultados) {
        // Calcular PIP/utilidad acumulados ANTES de ordenar para asignar posicion correctamente.
        // Bug fix: antes se ordenaba por pipTrimestre (Q actual) lo que daba posiciones incoherentes
        // con la performance acumulada en competencias multi-Q.
        Map<Long, BigDecimal> pipAcumuladoPrevio = new HashMap<>();
        List<RankingTrimestreEntity> rankingsPrevios = rankingRepo.findByCompetenciaId(competenciaId);
        for (RankingTrimestreEntity rp : rankingsPrevios) {
            pipAcumuladoPrevio.merge(rp.getEquipoId(), rp.getPipAcumulado(),
                    (old, nw) -> nw.compareTo(old) > 0 ? nw : old);
        }

        Map<Long, Long> utilidadAcumPrev = new HashMap<>();
        for (RankingTrimestreEntity rp : rankingsPrevios) {
            utilidadAcumPrev.merge(rp.getEquipoId(), rp.getUtilidadAcumulada(),
                    (old, nw) -> nw); // el último (por orden de trimestre) prevalece
        }

        // Pre-computar acumulados por equipo
        Map<Long, BigDecimal> pipAcumPorEquipo = new HashMap<>();
        Map<Long, Long> utilAcumPorEquipo = new HashMap<>();
        for (ResultadoEquipo r : resultados) {
            BigDecimal pipPrevio = pipAcumuladoPrevio.getOrDefault(r.equipoId, BigDecimal.ZERO);
            pipAcumPorEquipo.put(r.equipoId, pipPrevio.add(r.pipTrimestre));
            utilAcumPorEquipo.put(r.equipoId, utilidadAcumPrev.getOrDefault(r.equipoId, 0L) + r.utilidadNeta);
        }

        // Criterio de ranking: UTILIDAD ACUMULADA descendente (decisión de producto — feedback
        // prueba interna). El PIP se sigue calculando y persistiendo como indicador secundario de
        // gestión y se usa como desempate cuando dos equipos empatan en utilidad acumulada.
        List<ResultadoEquipo> ordenados = resultados.stream()
                .sorted(Comparator
                        .comparingLong((ResultadoEquipo r) -> utilAcumPorEquipo.get(r.equipoId)).reversed()
                        .thenComparing(Comparator.comparing((ResultadoEquipo r) -> pipAcumPorEquipo.get(r.equipoId)).reversed()))
                .toList();

        short posicion = 1;
        for (ResultadoEquipo r : ordenados) {
            RankingTrimestreEntity rank = new RankingTrimestreEntity();
            rank.setCompetenciaId(competenciaId);
            rank.setTrimestreId(trimestreId);
            rank.setEquipoId(r.equipoId);
            rank.setPosicion(posicion++);
            rank.setPipAcumulado(pipAcumPorEquipo.get(r.equipoId));
            rank.setUtilidadAcumulada(utilAcumPorEquipo.get(r.equipoId));
            rank.setCajaActual(r.cajaFinal);
            rank.setShareActual(r.share);
            rank.setCalculadoAt(OffsetDateTime.now());

            rankingRepo.save(rank);
        }
    }

    // ====================================================================
    // Finalizar competencia y crear snapshots para Q+1
    // ====================================================================

    private void finalizarCompetencia(CompetenciaEntity competencia, Long trimestreId,
                                       List<ResultadoEquipo> resultados,
                                       List<EquipoEntity> equipos) {
        // Obtener ranking final para asignar posición y PIP
        List<RankingTrimestreEntity> rankingFinal = rankingRepo
                .findByCompetenciaIdAndTrimestreId(competencia.getId(), trimestreId);

        Map<Long, EquipoEntity> equipoMap = equipos.stream()
                .collect(Collectors.toMap(EquipoEntity::getId, e -> e));

        for (RankingTrimestreEntity rank : rankingFinal) {
            EquipoEntity equipo = equipoMap.get(rank.getEquipoId());
            if (equipo != null) {
                equipo.setPosicionFinal((short) rank.getPosicion());
                equipo.setPipFinal(rank.getPipAcumulado());
                equipoRepo.save(equipo);
            }
        }

        // Transicionar competencia a PENDIENTE_FINALIZAR (el moderador finaliza manualmente)
        competencia.setEstado(CompetenciaStateMachine.PENDIENTE_FINALIZAR);
        competencia.setUpdatedAt(OffsetDateTime.now());
        competenciaRepo.save(competencia);

        log.info("Competencia {} pendiente de finalizar. Ranking asignado.", competencia.getCodigo());
    }

    private void crearSnapshotsInicioSiguiente(Long competenciaId, TrimestreEntity trimestreActual,
                                                List<ResultadoEquipo> resultados,
                                                Map<Long, SnapshotEstadoEntity> snapshotsInicio,
                                                Map<Long, DecisionEquipoEntity> decisiones) {
        // Snapshot INICIO del Q+1 = estado de CIERRE del Q actual
        short siguienteNumero = (short) (trimestreActual.getNumero() + 1);
        TrimestreEntity siguienteTri = trimestreRepo
                .findByCompetenciaIdAndNumero(competenciaId, siguienteNumero)
                .orElseThrow(() -> new IllegalStateException(
                        "Trimestre Q" + siguienteNumero + " no encontrado"));

        for (ResultadoEquipo r : resultados) {
            SnapshotEstadoEntity inicio = new SnapshotEstadoEntity();
            inicio.setEquipoId(r.equipoId);
            inicio.setTrimestreId(siguienteTri.getId());
            inicio.setMomento("INICIO");

            inicio.setCaja(r.cajaFinal);
            inicio.setDeuda(r.deudaFinal);
            inicio.setPatrimonioNeto(r.patrimonioNeto);
            inicio.setValorPlanta(r.valorPlantaFinal);

            inicio.setCapacidad(r.capacidadFinal);
            inicio.setHeadcount(r.headcountNuevo);
            inicio.setSalario(r.salarioNuevo);
            inicio.setInventario(r.inventarioFinal);

            inicio.setBrandEquity(r.brandEquity);
            inicio.setCalidadPercibida(r.calidadPercibida);
            inicio.setIdAcumulado(r.idAcumulado);

            inicio.setPip(r.pipTrimestre);

            snapshotRepo.save(inicio);
        }
    }

    // ====================================================================
    // Funciones auxiliares matemáticas
    // ====================================================================

    // ====================================================================
    // Auto-event effects (per team, from previous quarters)
    // ====================================================================

    /** Aggregated effects from auto-events affecting this team this quarter */
    private record AutoEventEffects(
            BigDecimal factorProduccion,
            BigDecimal factorDemanda,
            BigDecimal factorCapacidad,
            BigDecimal factorCostoFijo
    ) {
        static AutoEventEffects NONE = new AutoEventEffects(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
    }

    private AutoEventEffects calcularEfectosAutoEventos(Long competenciaId, Long equipoId, int qActual) {
        List<EventoAutomaticoAplicadoEntity> activos = eventoAutoService.getActivosParaEquipo(
                competenciaId, equipoId, qActual);
        if (activos.isEmpty()) return AutoEventEffects.NONE;

        BigDecimal factorProd = BigDecimal.ONE;
        BigDecimal factorDemanda = BigDecimal.ONE;
        BigDecimal factorCapacidad = BigDecimal.ONE;
        BigDecimal factorCostoFijo = BigDecimal.ONE;

        for (EventoAutomaticoAplicadoEntity aplicado : activos) {
            EventoAutomaticoReglaEntity regla = eventoAutoService.getRegla(aplicado.getReglaId());
            if (regla == null) continue;

            BigDecimal valor = regla.getEfectoValor();
            switch (regla.getEfectoTipo()) {
                case "PRODUCCION" -> factorProd = factorProd.add(valor);
                case "DEMANDA_TOTAL" -> factorDemanda = factorDemanda.add(valor);
                case "CAPACIDAD" -> factorCapacidad = factorCapacidad.add(valor);
                case "COSTO_FIJO" -> factorCostoFijo = factorCostoFijo.add(valor);
            }
            log.debug("Auto-evento '{}' aplicado a equipo {} en Q{}: {}={}",
                    regla.getNombre(), equipoId, qActual, regla.getEfectoTipo(), valor);
        }

        return new AutoEventEffects(factorProd, factorDemanda, factorCapacidad, factorCostoFijo);
    }

    // ====================================================================
    // Funciones auxiliares matemáticas
    // ====================================================================

    /** Factor de eficiencia: penalización fuera del rango óptimo 75-85% */
    private BigDecimal calcularFactorEficiencia(BigDecimal utilizacion) {
        double u = utilizacion.doubleValue();
        if (u >= 0.75 && u <= 0.85) {
            return BigDecimal.ONE;
        } else if (u < 0.75) {
            return BigDecimal.ONE.add(BigDecimal.valueOf((0.75 - u) * 0.8));
        } else {
            return BigDecimal.ONE.add(BigDecimal.valueOf((u - 0.85) * 1.2));
        }
    }

    /**
     * Inflación acumulada desde Q1 hasta el Q dado (producto de factores).
     * If Q exceeds stored quarters, cycles back: Q5 uses Q1's rate, etc.
     */
    private BigDecimal calcularInflacionAcumulada(List<ParametroMacroTrimestreEntity> trimestres, int q) {
        BigDecimal acum = BigDecimal.ONE;
        int maxQ = trimestres.size();
        for (int i = 1; i <= q; i++) {
            int qCiclico = maxQ > 0 ? ((i - 1) % maxQ) + 1 : 1;
            BigDecimal inflacion = findByTrimestre(trimestres, qCiclico)
                    .map(ParametroMacroTrimestreEntity::getInflacionTrim)
                    .orElse(BigDecimal.ZERO);
            acum = acum.multiply(BigDecimal.ONE.add(inflacion));
        }
        return acum;
    }

    /**
     * Obtener un valor macro para el Q dado desde la lista normalizada.
     * If Q exceeds stored quarters, cycles back: Q5 uses Q1's value, etc.
     */
    private BigDecimal obtenerValorMacroQ(List<ParametroMacroTrimestreEntity> trimestres, int q,
                                           Function<ParametroMacroTrimestreEntity, BigDecimal> extractor) {
        int maxQ = trimestres.size();
        int qCiclico = maxQ > 0 ? ((q - 1) % maxQ) + 1 : 1;
        return findByTrimestre(trimestres, qCiclico)
                .map(extractor)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Estacionalidad del rubro para el Q dado desde la lista normalizada.
     * If Q exceeds stored quarters, cycles back.
     */
    private BigDecimal obtenerEstacionalidad(List<ParametroRubroTrimestreEntity> trimestres, int q) {
        int maxQ = trimestres.size();
        int qCiclico = maxQ > 0 ? ((q - 1) % maxQ) + 1 : 1;
        return findByTrimestre(trimestres, qCiclico)
                .map(ParametroRubroTrimestreEntity::getEstacionalidad)
                .orElse(BigDecimal.ONE);
    }

    /** Find a trimestre entity by its trimestre number */
    private <T> Optional<T> findByTrimestre(List<T> list, int trimestre) {
        // List is sorted by trimestre, so direct index access if possible
        int idx = trimestre - 1;
        if (idx >= 0 && idx < list.size()) {
            return Optional.of(list.get(idx));
        }
        return Optional.empty();
    }

    /** Orden de severidad: mayor número = mayor prioridad para override de pesos */
    private int severidadOrden(String severidad) {
        if (severidad == null) return 0;
        return switch (severidad.toUpperCase()) {
            case "POSITIVO" -> 1;
            case "LEVE" -> 2;
            case "MODERADO" -> 3;
            case "GRAVE" -> 4;
            default -> 0;
        };
    }

    /** Potencia para BigDecimal: base^exp usando Math.pow */
    private BigDecimal pow(BigDecimal base, BigDecimal exponent) {
        if (base.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(Math.pow(base.doubleValue(), exponent.doubleValue()));
    }

    /** Raíz cuadrada para BigDecimal */
    private BigDecimal sqrt(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(Math.sqrt(value.doubleValue()));
    }

    /** Normalización min-max a escala 0-100. Si min==max, retorna 50. */
    private double normalizar(double valor, double min, double max) {
        if (max == min) return 50.0;
        return ((valor - min) / (max - min)) * 100.0;
    }

    private double normalizar(long valor, long min, long max) {
        return normalizar((double) valor, (double) min, (double) max);
    }

    private long min(long[] arr) {
        long m = Long.MAX_VALUE;
        for (long v : arr) if (v < m) m = v;
        return m;
    }

    private long max(long[] arr) {
        long m = Long.MIN_VALUE;
        for (long v : arr) if (v > m) m = v;
        return m;
    }

    private double min(double[] arr) {
        double m = Double.MAX_VALUE;
        for (double v : arr) if (v < m) m = v;
        return m;
    }

    private double max(double[] arr) {
        double m = -Double.MAX_VALUE;
        for (double v : arr) if (v > m) m = v;
        return m;
    }
}

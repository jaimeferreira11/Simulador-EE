package py.simulador.decision;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.api.generated.model.DecisionInput;
import py.simulador.catalogo.*;
import py.simulador.common.AccessDeniedException;
import py.simulador.common.ResourceNotFoundException;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.config.SecurityUtils;
import py.simulador.equipo.EquipoEntity;
import py.simulador.equipo.EquipoMiembroEntity;
import py.simulador.equipo.EquipoMiembroRepository;
import py.simulador.equipo.EquipoRepository;
import py.simulador.evento.EventoCompetenciaEntity;
import py.simulador.evento.EventoCompetenciaRepository;
import py.simulador.motor.CalculoCostoMp;
import py.simulador.resultado.RankingTrimestreEntity;
import py.simulador.resultado.RankingTrimestreRepository;
import py.simulador.resultado.SnapshotEstadoEntity;
import py.simulador.resultado.SnapshotEstadoRepository;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static py.simulador.decision.ContextoDecisionDTO.*;

@Service
public class ContextoDecisionService {

    private final TrimestreRepository trimestreRepo;
    private final CompetenciaRepository competenciaRepo;
    private final EquipoRepository equipoRepo;
    private final EquipoMiembroRepository miembroRepo;
    private final SnapshotEstadoRepository snapshotRepo;
    private final RankingTrimestreRepository rankingRepo;
    private final EventoCompetenciaRepository eventoCompRepo;
    private final EventoCatalogoRepository eventoCatalogoRepo;
    private final DecisionEquipoRepository decisionRepo;
    private final AreaDecisionRepository areaRepo;
    private final ParametroRubroRepository parametroRubroRepo;
    private final ParametroMacroRepository parametroMacroRepo;
    private final ParametroMacroTrimestreRepository macroTrimestreRepo;
    private final ParametroRubroTrimestreRepository rubroTrimestreRepo;
    private final RubroRepository rubroRepo;
    private final MateriaPrimaRubroRepository materiaPrimaRepo;

    public ContextoDecisionService(TrimestreRepository trimestreRepo,
                                    CompetenciaRepository competenciaRepo,
                                    EquipoRepository equipoRepo,
                                    EquipoMiembroRepository miembroRepo,
                                    SnapshotEstadoRepository snapshotRepo,
                                    RankingTrimestreRepository rankingRepo,
                                    EventoCompetenciaRepository eventoCompRepo,
                                    EventoCatalogoRepository eventoCatalogoRepo,
                                    DecisionEquipoRepository decisionRepo,
                                    AreaDecisionRepository areaRepo,
                                    ParametroRubroRepository parametroRubroRepo,
                                    ParametroMacroRepository parametroMacroRepo,
                                    ParametroMacroTrimestreRepository macroTrimestreRepo,
                                    ParametroRubroTrimestreRepository rubroTrimestreRepo,
                                    RubroRepository rubroRepo,
                                    MateriaPrimaRubroRepository materiaPrimaRepo) {
        this.trimestreRepo = trimestreRepo;
        this.competenciaRepo = competenciaRepo;
        this.equipoRepo = equipoRepo;
        this.miembroRepo = miembroRepo;
        this.snapshotRepo = snapshotRepo;
        this.rankingRepo = rankingRepo;
        this.eventoCompRepo = eventoCompRepo;
        this.eventoCatalogoRepo = eventoCatalogoRepo;
        this.decisionRepo = decisionRepo;
        this.areaRepo = areaRepo;
        this.parametroRubroRepo = parametroRubroRepo;
        this.parametroMacroRepo = parametroMacroRepo;
        this.macroTrimestreRepo = macroTrimestreRepo;
        this.rubroTrimestreRepo = rubroTrimestreRepo;
        this.rubroRepo = rubroRepo;
        this.materiaPrimaRepo = materiaPrimaRepo;
    }

    @Transactional(readOnly = true)
    public ContextoDecisionDTO buildContexto(Long equipoId, Long trimestreId) {
        Long usuarioId = SecurityUtils.getUserId();

        TrimestreEntity trimestre = trimestreRepo.findById(trimestreId)
                .orElseThrow(() -> new ResourceNotFoundException("Trimestre", trimestreId));

        CompetenciaEntity comp = competenciaRepo.findById(trimestre.getCompetenciaId())
                .orElseThrow(() -> new ResourceNotFoundException("Competencia", trimestre.getCompetenciaId()));

        EquipoEntity equipo = equipoRepo.findById(equipoId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipo", equipoId));

        // Verificar que el equipo pertenece a la competencia del trimestre
        if (!equipo.getCompetenciaId().equals(comp.getId())) {
            throw new AccessDeniedException("El equipo no pertenece a la competencia de este trimestre");
        }

        EquipoMiembroEntity miembro = miembroRepo.findByEquipoIdAndUsuarioId(equipoId, usuarioId)
                .orElseThrow(() -> new AccessDeniedException("No eres miembro de este equipo"));

        // 1. Trimestre info
        TrimestreInfo triInfo = new TrimestreInfo(
                trimestre.getNumero(),
                trimestre.getEstado(),
                trimestre.getCierreAt() != null ? trimestre.getCierreAt().toString() : null
        );

        // 2. Snapshot de inicio
        SnapshotInicioDTO snapshotDto = buildSnapshot(equipoId, trimestreId, comp);

        // 3. Datos de mercado
        MercadoDTO mercadoDto = buildMercado(comp, trimestre, equipoId, trimestreId);

        // 4. Ranking anterior
        List<RankingItemDTO> ranking = buildRanking(comp, trimestre);

        // 5. Eventos activos
        List<EventoActivoDTO> eventos = buildEventos(comp.getId(), trimestreId, trimestre.getNumero());

        // 6. Decision anterior (Q-1)
        DecisionAnteriorDTO decAnt = buildDecisionAnterior(equipoId, comp.getId(), trimestre.getNumero());

        // 7. Limites
        LimitesDTO limites = buildLimites(equipoId, trimestreId, comp);

        // 8. Permisos
        PermisosDTO permisos = buildPermisos(miembro);

        // 9. Pesos de competitividad (base o override por evento)
        PesosDTO pesos = buildPesos(comp, eventos);

        // 10. Costo unitario de MP del trimestre (sin factor eficiencia) para preview del jugador
        long costoUnitarioMp = buildCostoUnitarioMp(comp, trimestre);

        // 11. Producto del rubro + BOM (narrativo, READ-ONLY)
        ProductoRubroDTO producto = buildProducto(comp.getRubroId());

        return new ContextoDecisionDTO(
                triInfo, snapshotDto, mercadoDto, ranking, eventos,
                decAnt, limites, permisos, pesos, costoUnitarioMp, producto
        );
    }

    /**
     * Construye el DTO descriptivo del producto del rubro y su Bill of Materials.
     * Devuelve {@code null} si el rubro no tiene producto definido
     * ({@code producto_nombre} null). El {@code costoBaseUnitario} es la SUMA de
     * los costos unitarios del BOM (igual por construccion a
     * {@code parametro_rubro.costo_unit_mp}).
     */
    ProductoRubroDTO buildProducto(Long rubroId) {
        RubroEntity rubro = rubroRepo.findById(rubroId).orElse(null);
        if (rubro == null || rubro.getProductoNombre() == null) {
            return null;
        }

        List<MateriaPrimaRubroEntity> bom = materiaPrimaRepo.findByRubroIdOrderByOrdenAsc(rubroId);

        long costoBaseUnitario = bom.stream()
                .mapToLong(MateriaPrimaRubroEntity::getCostoUnitario)
                .sum();

        List<MateriaPrimaDTO> materias = bom.stream()
                .map(mp -> new MateriaPrimaDTO(mp.getNombre(), mp.getCostoUnitario()))
                .toList();

        return new ProductoRubroDTO(
                rubro.getProductoNombre(),
                rubro.getProductoDescripcion(),
                rubro.getUnidadMedida(),
                costoBaseUnitario,
                materias
        );
    }

    // ========================================================================
    // Builders
    // ========================================================================

    private SnapshotInicioDTO buildSnapshot(Long equipoId, Long trimestreId, CompetenciaEntity comp) {
        Optional<SnapshotEstadoEntity> snapOpt = snapshotRepo
                .findByEquipoIdAndTrimestreIdAndMomento(equipoId, trimestreId, "INICIO");

        if (snapOpt.isPresent()) {
            SnapshotEstadoEntity s = snapOpt.get();
            return new SnapshotInicioDTO(
                    s.getCaja(), s.getDeuda(), s.getPatrimonioNeto(),
                    s.getCapacidad(), s.getHeadcount(), s.getSalario(),
                    s.getInventario(),
                    d(s.getBrandEquity()), d(s.getCalidadPercibida()),
                    s.getIdAcumulado(), d(s.getPip())
            );
        }

        // Q1 — no hay snapshot previo, usar valores iniciales de la competencia
        return new SnapshotInicioDTO(
                comp.getCajaInicial(), 0, comp.getCajaInicial(),
                comp.getCapacidadInicial(), comp.getHeadcountInicial(),
                comp.getSalarioInicial(), comp.getInventarioInicial(),
                d(null), 0, 0, 0
        );
    }

    private MercadoDTO buildMercado(CompetenciaEntity comp, TrimestreEntity tri,
                                     Long equipoId, Long trimestreId) {
        ParametroRubroEntity pr = parametroRubroRepo.findByRubroIdActivos(comp.getRubroId())
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("ParametroRubro", "rubro", String.valueOf(comp.getRubroId())));

        ParametroMacroEntity pm = parametroMacroRepo.findById(comp.getParametroMacroId())
                .orElseThrow(() -> new ResourceNotFoundException("ParametroMacro", comp.getParametroMacroId()));

        // Demanda con estacionalidad (from normalized table, cyclic fallback)
        List<ParametroRubroTrimestreEntity> rubroTrimestres =
                rubroTrimestreRepo.findByRubroParamId(pr.getId());
        int maxQRubro = rubroTrimestres.size();
        int qCiclico = maxQRubro > 0 ? ((tri.getNumero() - 1) % maxQRubro) + 1 : 1;
        BigDecimal estacionalidad = rubroTrimestres.stream()
                .filter(rt -> rt.getTrimestre() == qCiclico)
                .map(ParametroRubroTrimestreEntity::getEstacionalidad)
                .findFirst().orElse(BigDecimal.ONE);
        long demandaEstimada = BigDecimal.valueOf(pr.getDemandaBaseTrim())
                .multiply(estacionalidad).longValue();

        // Inflacion acumulada hasta este Q (from normalized table)
        List<ParametroMacroTrimestreEntity> macroTrimestres =
                macroTrimestreRepo.findByMacroId(pm.getId());
        double inflacionAcum = calcularInflacionAcumulada(macroTrimestres, tri.getNumero());

        // Promedios de mercado: si hay snapshots INICIO de este Q, usarlos; sino usar valores del rubro
        List<SnapshotEstadoEntity> snaps = snapshotRepo
                .findByTrimestreIdAndMomento(trimestreId, "INICIO");

        long idAcumPromedio = 0;
        long marketingPromedio = 0;
        if (!snaps.isEmpty()) {
            idAcumPromedio = snaps.stream()
                    .mapToLong(SnapshotEstadoEntity::getIdAcumulado)
                    .sum() / snaps.size();
        }

        // Marketing promedio: de las decisiones del Q anterior
        if (tri.getNumero() > 1) {
            Optional<TrimestreEntity> triAnt = trimestreRepo
                    .findByCompetenciaIdAndNumero(comp.getId(), (short) (tri.getNumero() - 1));
            if (triAnt.isPresent()) {
                List<DecisionEquipoEntity> decsAnt = decisionRepo.findByTrimestreId(triAnt.get().getId());
                if (!decsAnt.isEmpty()) {
                    marketingPromedio = decsAnt.stream()
                            .mapToLong(DecisionEquipoEntity::getInversionMarketing)
                            .sum() / decsAnt.size();
                }
            }
        }

        return new MercadoDTO(
                demandaEstimada,
                pr.getPrecioReferencia(),
                idAcumPromedio,
                marketingPromedio,
                inflacionAcum
        );
    }

    private List<RankingItemDTO> buildRanking(CompetenciaEntity comp, TrimestreEntity tri) {
        if (tri.getNumero() <= 1) return List.of();

        // Buscar ultimo trimestre procesado
        Optional<TrimestreEntity> triProcesado = trimestreRepo
                .findUltimoTrimestreProcesado(comp.getId());
        if (triProcesado.isEmpty()) return List.of();

        List<RankingTrimestreEntity> rankings = rankingRepo
                .findByCompetenciaIdAndTrimestreId(comp.getId(), triProcesado.get().getId());

        Map<Long, EquipoEntity> equipos = equipoRepo.findByCompetenciaId(comp.getId()).stream()
                .collect(Collectors.toMap(EquipoEntity::getId, e -> e));

        return rankings.stream().map(r -> new RankingItemDTO(
                r.getPosicion(),
                r.getEquipoId(),
                equipos.containsKey(r.getEquipoId()) ? equipos.get(r.getEquipoId()).getNombreEmpresa() : "Equipo " + r.getEquipoId(),
                d(r.getPipAcumulado()),
                d(r.getShareActual())
        )).toList();
    }

    private List<EventoActivoDTO> buildEventos(Long competenciaId, Long trimestreId, short numTrimestre) {
        List<EventoCompetenciaEntity> activos = eventoCompRepo
                .findActivosParaTrimestre(competenciaId, trimestreId);

        // Pre-cargar catalogos
        Map<Long, EventoCatalogoEntity> catalogoMap = new HashMap<>();
        for (EventoCompetenciaEntity ec : activos) {
            catalogoMap.computeIfAbsent(ec.getEventoCatalogoId(),
                    id -> eventoCatalogoRepo.findById(id).orElse(null));
        }

        // Pre-cargar trimestres origen para calcular duracion restante
        Map<Long, TrimestreEntity> trimestreMap = new HashMap<>();
        for (EventoCompetenciaEntity ec : activos) {
            trimestreMap.computeIfAbsent(ec.getTrimestreId(),
                    id -> trimestreRepo.findById(id).orElse(null));
        }

        return activos.stream().map(ec -> {
            EventoCatalogoEntity cat = catalogoMap.get(ec.getEventoCatalogoId());
            if (cat == null) return null;

            TrimestreEntity triOrigen = trimestreMap.get(ec.getTrimestreId());
            int durRestante = ec.getDuracionAplicada()
                    - (numTrimestre - (triOrigen != null ? triOrigen.getNumero() : numTrimestre));

            // Determinar areas impactadas segun tipo de efecto
            List<String> areas = inferirAreasImpactadas(cat);

            PesosDTO override = null;
            if (cat.getOverridePesoPrecio() != null) {
                override = new PesosDTO(
                        d(cat.getOverridePesoPrecio()),
                        d(cat.getOverridePesoMarketing()),
                        d(cat.getOverridePesoCalidad()),
                        d(cat.getOverridePesoMarca())
                );
            }

            return new EventoActivoDTO(
                    cat.getNombre(),
                    cat.getSeveridad(),
                    cat.getTipoEfecto(),
                    d(ec.getMagnitudAplicada()),
                    Math.max(durRestante, 0),
                    cat.getDescripcion(),
                    areas,
                    override
            );
        }).filter(Objects::nonNull).toList();
    }

    private DecisionAnteriorDTO buildDecisionAnterior(Long equipoId, Long competenciaId, short numTrimestre) {
        if (numTrimestre <= 1) return null;

        Optional<TrimestreEntity> triAnt = trimestreRepo
                .findByCompetenciaIdAndNumero(competenciaId, (short) (numTrimestre - 1));
        if (triAnt.isEmpty()) return null;

        Optional<DecisionEquipoEntity> decOpt = decisionRepo
                .findByEquipoIdAndTrimestreId(equipoId, triAnt.get().getId());
        if (decOpt.isEmpty()) return null;

        DecisionEquipoEntity dec = decOpt.get();
        return new DecisionAnteriorDTO(
                dec.getPrecioVenta(), dec.getInversionMarketing(),
                dec.getProduccionPlanificada(),
                dec.getInversionCapacidad(), dec.getInversionId(),
                dec.getContratacionesNetas(),
                d(dec.getAumentoSalarialPct()),
                dec.getInversionCapacitacion(),
                dec.getPrestamoSolicitado(), dec.getDividendosPagar()
        );
    }

    private LimitesDTO buildLimites(Long equipoId, Long trimestreId, CompetenciaEntity comp) {
        Optional<SnapshotEstadoEntity> snapOpt = snapshotRepo
                .findByEquipoIdAndTrimestreIdAndMomento(equipoId, trimestreId, "INICIO");

        long prestamoMax = 0;
        long dividendoMax = 0;
        long capacidadMaxProd = 0;
        boolean puedePedir = true;
        String razonBloqueo = null;

        if (snapOpt.isPresent()) {
            SnapshotEstadoEntity snap = snapOpt.get();

            // RN-FIN-001: prestamo max = 2x patrimonio neto
            prestamoMax = Math.max(0, snap.getPatrimonioNeto() * 2);

            // RN-FIN-003: bloquear si deuda/activo > 1.0
            long activos = snap.getCaja() + snap.getValorPlanta() + snap.getInventario();
            if (activos > 0 && snap.getDeuda() > activos) {
                puedePedir = false;
                razonBloqueo = "Ratio deuda/activo superior a 1.0";
                prestamoMax = 0;
            }

            // Dividendo max = 50% utilidad acumulada (pip * factor)
            if (snap.getPip() != null && snap.getPip().compareTo(BigDecimal.ZERO) > 0) {
                dividendoMax = snap.getPip().multiply(BigDecimal.valueOf(500_000)).longValue() / 2;
            }

            // Capacidad maxima produccion
            capacidadMaxProd = snap.getCapacidad();
        } else {
            // Q1 — usar valores iniciales
            capacidadMaxProd = comp.getCapacidadInicial();
            prestamoMax = comp.getCajaInicial() * 2;
        }

        // Salario minimo legal
        ParametroMacroEntity pm = parametroMacroRepo.findById(comp.getParametroMacroId()).orElse(null);
        long salarioMin = pm != null ? pm.getSalarioMinimoQ1() : 0;

        return new LimitesDTO(prestamoMax, dividendoMax, capacidadMaxProd,
                salarioMin, puedePedir, razonBloqueo);
    }

    private PermisosDTO buildPermisos(EquipoMiembroEntity miembro) {
        String areaAsignada = null;
        List<String> camposEditables;

        if (miembro.isEsCapitan()) {
            camposEditables = List.of(
                    "precio_venta", "inversion_marketing",
                    "produccion_planificada", "inversion_capacidad", "inversion_id",
                    "contrataciones_netas", "aumento_salarial_pct", "inversion_capacitacion",
                    "prestamo_solicitado", "dividendos_pagar"
            );
        } else if (miembro.getAreaId() != null) {
            AreaDecisionEntity area = areaRepo.findById(miembro.getAreaId()).orElse(null);
            if (area != null) {
                areaAsignada = area.getCodigo();
                camposEditables = Arrays.asList(area.getCampos());
            } else {
                camposEditables = List.of();
            }
        } else {
            camposEditables = List.of();
        }

        return new PermisosDTO(miembro.isEsCapitan(), areaAsignada, camposEditables);
    }

    /**
     * Costo unitario de MP del trimestre abierto, SIN el factor de eficiencia (que depende de
     * la producción que el jugador aún no decidió). Usa exactamente la misma fórmula que el
     * motor a través de {@link CalculoCostoMp}, de modo que el preview "Gs X por unidad" sea
     * consistente con el cálculo real al cerrar el trimestre.
     */
    private long buildCostoUnitarioMp(CompetenciaEntity comp, TrimestreEntity tri) {
        ParametroRubroEntity pr = parametroRubroRepo.findByRubroIdActivos(comp.getRubroId())
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("ParametroRubro", "rubro", String.valueOf(comp.getRubroId())));

        ParametroMacroEntity pm = parametroMacroRepo.findById(comp.getParametroMacroId())
                .orElseThrow(() -> new ResourceNotFoundException("ParametroMacro", comp.getParametroMacroId()));

        List<ParametroMacroTrimestreEntity> macroTrimestres =
                macroTrimestreRepo.findByMacroId(pm.getId());

        int q = tri.getNumero();
        BigDecimal inflacionAcum = calcularInflacionAcumuladaFactor(macroTrimestres, q);
        BigDecimal tipoCambio = obtenerValorMacroQ(macroTrimestres, q,
                ParametroMacroTrimestreEntity::getTipoCambio);

        FactoresEvento factores = buildFactoresEvento(comp, tri);

        return CalculoCostoMp.costoUnitarioSinEficienciaRedondeado(
                pr.getCostoUnitMp(), inflacionAcum, tipoCambio,
                pr.getPctMpImportada(), factores.costoMp(), factores.costoLog());
    }

    /** Factores multiplicativos de evento (1 + Σ magnitud) para costos del trimestre. */
    private record FactoresEvento(BigDecimal costoMp, BigDecimal costoLog, BigDecimal costoFijo) {}

    /**
     * Calcula los factores de evento activos este Q (MP, logístico y costo fijo) en una sola
     * pasada, con EXACTAMENTE la misma resolución de magnitud que {@link MotorSimulacion}
     * (magnitudAplicada, o magnitudDefault del catálogo cuando aquélla es null). Compartido por
     * el costo unitario de MP y la proyección de caja para no divergir del motor.
     */
    private FactoresEvento buildFactoresEvento(CompetenciaEntity comp, TrimestreEntity tri) {
        BigDecimal factorCostoMp = BigDecimal.ONE;
        BigDecimal factorCostoLog = BigDecimal.ONE;
        BigDecimal factorCostoFijo = BigDecimal.ONE;

        List<EventoCompetenciaEntity> activos = eventoCompRepo
                .findActivosParaTrimestre(comp.getId(), tri.getId());

        // Pre-cargar catalogos (mismo patron que buildEventos) para evitar N+1.
        Map<Long, EventoCatalogoEntity> catalogoMap = new HashMap<>();
        for (EventoCompetenciaEntity ec : activos) {
            catalogoMap.computeIfAbsent(ec.getEventoCatalogoId(),
                    id -> eventoCatalogoRepo.findById(id).orElse(null));
        }

        for (EventoCompetenciaEntity ec : activos) {
            EventoCatalogoEntity cat = catalogoMap.get(ec.getEventoCatalogoId());
            if (cat == null) continue;

            String tipo = cat.getTipoEfecto();
            if (tipo == null) continue;

            BigDecimal mag = CalculoCostoMp.magnitudEfectiva(
                    ec.getMagnitudAplicada(), cat.getMagnitudDefault());
            if (mag == null) continue;

            switch (tipo) {
                case "COSTO_MP" -> factorCostoMp = factorCostoMp.add(mag);
                case "COSTO_LOGISTICO" -> factorCostoLog = factorCostoLog.add(mag);
                case "COSTO_FIJO" -> factorCostoFijo = factorCostoFijo.add(mag);
                default -> { /* otros tipos (DEMANDA_TOTAL, etc.) no afectan costos aquí */ }
            }
        }

        return new FactoresEvento(factorCostoMp, factorCostoLog, factorCostoFijo);
    }

    /**
     * Inflación acumulada desde Q1 hasta el Q dado como FACTOR (≥ 1.0), idéntico al motor.
     * Distinto de {@link #calcularInflacionAcumulada} que devuelve el porcentaje (factor - 1).
     */
    private BigDecimal calcularInflacionAcumuladaFactor(List<ParametroMacroTrimestreEntity> trimestres, int q) {
        BigDecimal acum = BigDecimal.ONE;
        int maxQ = trimestres.size();
        for (int i = 1; i <= q; i++) {
            int qCiclico = maxQ > 0 ? ((i - 1) % maxQ) + 1 : 1;
            BigDecimal inflacion = findByTrimestreNum(trimestres, qCiclico)
                    .map(ParametroMacroTrimestreEntity::getInflacionTrim)
                    .orElse(BigDecimal.ZERO);
            acum = acum.multiply(BigDecimal.ONE.add(inflacion));
        }
        return acum;
    }

    private BigDecimal obtenerValorMacroQ(List<ParametroMacroTrimestreEntity> trimestres, int q,
                                          java.util.function.Function<ParametroMacroTrimestreEntity, BigDecimal> extractor) {
        int maxQ = trimestres.size();
        int qCiclico = maxQ > 0 ? ((q - 1) % maxQ) + 1 : 1;
        return findByTrimestreNum(trimestres, qCiclico)
                .map(extractor)
                .orElse(BigDecimal.ZERO);
    }

    private Optional<ParametroMacroTrimestreEntity> findByTrimestreNum(
            List<ParametroMacroTrimestreEntity> trimestres, int trimestre) {
        int idx = trimestre - 1;
        if (idx >= 0 && idx < trimestres.size()) {
            return Optional.of(trimestres.get(idx));
        }
        return Optional.empty();
    }

    private PesosDTO buildPesos(CompetenciaEntity comp, List<EventoActivoDTO> eventos) {
        // Si algun evento tiene override de pesos, usar el primero (prioridad por orden)
        for (EventoActivoDTO ev : eventos) {
            if (ev.overridePesos() != null) {
                return ev.overridePesos();
            }
        }

        // Pesos base del rubro
        ParametroRubroEntity pr = parametroRubroRepo.findByRubroIdActivos(comp.getRubroId())
                .stream().findFirst().orElse(null);
        if (pr != null) {
            return new PesosDTO(
                    d(pr.getPesoPrecio()), d(pr.getPesoMarketing()),
                    d(pr.getPesoCalidad()), d(pr.getPesoMarca())
            );
        }

        // Fallback: pesos retail default
        return new PesosDTO(0.40, 0.30, 0.20, 0.10);
    }

    // ========================================================================
    // Proyeccion financiera what-if
    // ========================================================================

    @Transactional(readOnly = true)
    public ProyeccionFinancieraDTO calcularProyeccion(Long equipoId, Long trimestreId,
                                                       DecisionInput input) {
        TrimestreEntity tri = trimestreRepo.findById(trimestreId)
                .orElseThrow(() -> new ResourceNotFoundException("Trimestre", trimestreId));

        CompetenciaEntity comp = competenciaRepo.findById(tri.getCompetenciaId())
                .orElseThrow(() -> new ResourceNotFoundException("Competencia", tri.getCompetenciaId()));

        ParametroRubroEntity pr = parametroRubroRepo.findByRubroIdActivos(comp.getRubroId())
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("ParametroRubro", "rubro", String.valueOf(comp.getRubroId())));

        ParametroMacroEntity pm = parametroMacroRepo.findById(comp.getParametroMacroId())
                .orElseThrow(() -> new ResourceNotFoundException("ParametroMacro", comp.getParametroMacroId()));

        // Snapshot de inicio
        Optional<SnapshotEstadoEntity> snapOpt = snapshotRepo
                .findByEquipoIdAndTrimestreIdAndMomento(equipoId, trimestreId, "INICIO");

        long caja, capacidad, deuda, inventario, salario, valorPlanta;
        int headcount;
        long idAcumulado;

        if (snapOpt.isPresent()) {
            SnapshotEstadoEntity s = snapOpt.get();
            caja = s.getCaja();
            capacidad = s.getCapacidad();
            deuda = s.getDeuda();
            inventario = s.getInventario();
            headcount = s.getHeadcount();
            salario = s.getSalario();
            idAcumulado = s.getIdAcumulado();
            valorPlanta = s.getValorPlanta();
        } else {
            caja = comp.getCajaInicial();
            capacidad = comp.getCapacidadInicial();
            deuda = 0;
            inventario = comp.getInventarioInicial();
            headcount = comp.getHeadcountInicial();
            salario = comp.getSalarioInicial();
            idAcumulado = 0;
            valorPlanta = comp.getValorPlantaInicial();
        }

        // --- Calculos de proyeccion ---

        long precioVenta = input.getPrecioVenta();
        long produccion = input.getProduccionPlanificada() != null ? input.getProduccionPlanificada() : 0;
        long invMarketing = input.getInversionMarketing() != null ? input.getInversionMarketing() : 0;
        long invCapacidad = input.getInversionCapacidad() != null ? input.getInversionCapacidad() : 0;
        long invId = input.getInversionId() != null ? input.getInversionId() : 0;
        long invCapacitacion = input.getInversionCapacitacion() != null ? input.getInversionCapacitacion() : 0;
        long prestamo = input.getPrestamoSolicitado() != null ? input.getPrestamoSolicitado() : 0;
        long dividendos = input.getDividendosPagar() != null ? input.getDividendosPagar() : 0;
        int contrataciones = input.getContratacionesNetas() != null ? input.getContratacionesNetas() : 0;
        double aumentoSalarial = input.getAumentoSalarialPct() != null ? input.getAumentoSalarialPct() : 0;

        // Producción efectiva: capada por la capacidad vigente. La inversión en capacidad
        // recién rige en Q+1 (lag=1), igual que el motor (produccionReal = min(plan, capacidad)).
        long produccionReal = Math.min(produccion, capacidad);

        // Capacidad nueva (sólo informativa; la expansión se efectiviza en Q+1)
        long capacidadNueva = capacidad;
        if (invCapacidad > 0 && pr.getCostoExpansionCapacidad() > 0) {
            capacidadNueva += invCapacidad / pr.getCostoExpansionCapacidad();
        }

        // Utilización sobre la capacidad vigente (la que efectivamente limita la producción)
        double utilizacion = capacidad > 0 ? (double) produccion / capacidad : 0;

        // Ingresos: estimación conservadora venta = producción (sin calcular market share real).
        // Bajo ese supuesto, el inventario remanente al cierre es el inventario inicial.
        long ventasEstimadas = produccionReal;
        long ingresosEstimados = ventasEstimadas * precioVenta;
        long inventarioFinal = inventario;

        // ── Costos: espejo de MotorSimulacion.completarCalculo, con los mismos parámetros ──
        FactoresEvento factores = buildFactoresEvento(comp, tri);
        List<ParametroMacroTrimestreEntity> macroTrims =
                macroTrimestreRepo.findByMacroId(pm.getId());
        int q = tri.getNumero();
        BigDecimal inflacionAcum = calcularInflacionAcumuladaFactor(macroTrims, q);

        // Costo unitario de MP ajustado (inflación + mix TC + eventos) — misma fuente que el motor.
        long costoUnitMpAjustado = buildCostoUnitarioMp(comp, tri);
        long costoMpTotal = produccionReal * costoUnitMpAjustado;

        // Costos fijos = base × inflación acumulada × factor evento de costo fijo.
        long costoFijo = BigDecimal.valueOf(pr.getCostosFijosTrim())
                .multiply(inflacionAcum)
                .multiply(factores.costoFijo())
                .setScale(0, RoundingMode.HALF_UP).longValue();

        // Costo laboral = salario_nuevo × headcount × 3 meses × (1 + IPS) + aguinaldo prorrateado.
        // aumentoSalarial es FRACCIÓN (0.05 = 5%), igual que la entidad/motor (no se divide /100).
        int headcountNuevo = headcount + contrataciones;
        long salarioNuevo = BigDecimal.valueOf(salario)
                .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(aumentoSalarial)))
                .setScale(0, RoundingMode.HALF_UP).longValue();
        long salXhcX3 = salarioNuevo * headcountNuevo * 3L;
        long costoLaboral = BigDecimal.valueOf(salXhcX3)
                .multiply(BigDecimal.ONE.add(pm.getIpsPatronal()))
                .add(BigDecimal.valueOf(salXhcX3).multiply(pm.getAguinaldoFactor()))
                .setScale(0, RoundingMode.HALF_UP).longValue();

        // Inversiones que el motor trata como costo operativo (reducen utilidad e IRE).
        long costoMarketing = invMarketing;
        long costoIdGasto = invId;
        long costoCapacitacion = invCapacitacion;

        // Almacenamiento = inventario final × costo unit MP ajustado × 2% (constante del motor).
        long costoAlmacenamiento = BigDecimal.valueOf(inventarioFinal)
                .multiply(BigDecimal.valueOf(costoUnitMpAjustado))
                .multiply(new BigDecimal("0.02"))
                .setScale(0, RoundingMode.HALF_UP).longValue();

        // Depreciación = valor planta × tasa trimestral. No es flujo de caja (se vuelve a sumar),
        // pero entra como costo para el cálculo de utilidad e IRE.
        long depreciacion = BigDecimal.valueOf(valorPlanta)
                .multiply(pr.getDepreciacionTrim())
                .setScale(0, RoundingMode.HALF_UP).longValue();

        // Intereses = deuda al inicio × tasa trimestral (TPM del Q + spread)/4. El préstamo nuevo
        // recién devenga intereses en Q+1, por eso NO se incluye en la base.
        BigDecimal tpmAnual = obtenerValorMacroQ(macroTrims, q,
                ParametroMacroTrimestreEntity::getTpmAnual);
        BigDecimal spread = pr.getSpreadTasa() != null ? pr.getSpreadTasa() : BigDecimal.ZERO;
        BigDecimal tasaTrim = tpmAnual.add(spread)
                .divide(new BigDecimal("4"), 8, RoundingMode.HALF_UP);
        long intereses = BigDecimal.valueOf(deuda).multiply(tasaTrim)
                .setScale(0, RoundingMode.HALF_UP).longValue();

        // ── Utilidad e impuesto (igual que el motor) ──
        long costosOperativosTotal = costoMpTotal + costoLaboral + costoFijo
                + costoMarketing + costoIdGasto + costoCapacitacion
                + costoAlmacenamiento + depreciacion;
        long utilidadOperativa = ingresosEstimados - costosOperativosTotal;
        long utilidadAntesImpuestos = utilidadOperativa - intereses;
        long impuestoIre = utilidadAntesImpuestos > 0
                ? BigDecimal.valueOf(utilidadAntesImpuestos).multiply(pm.getTasaIre())
                        .setScale(0, RoundingMode.HALF_UP).longValue()
                : 0;
        long utilidadNeta = utilidadAntesImpuestos - impuestoIre;

        // ── Caja proyectada al cierre: misma identidad que el motor ──
        // caja_ini + utilidadNeta + depreciación + préstamo − invCapacidad − dividendos.
        // (marketing/I+D/capacitación ya están dentro de utilidadNeta, no se restan otra vez.)
        long cajaProyectada = caja + utilidadNeta + depreciacion
                + prestamo - invCapacidad - dividendos;

        // Campos de display del DTO.
        long costosVariables = costoMpTotal;
        long costosFijos = costoFijo;
        long inversionTotal = invCapacidad + invMarketing + invId + invCapacitacion;

        // Semaforo
        String semaforo;
        if (cajaProyectada > costosFijos * 2) {
            semaforo = "verde";
        } else if (cajaProyectada > 0) {
            semaforo = "amarillo";
        } else {
            semaforo = "rojo";
        }

        // Advertencias
        List<ProyeccionFinancieraDTO.AdvertenciaDTO> advertencias = new ArrayList<>();

        if (cajaProyectada < 0) {
            advertencias.add(new ProyeccionFinancieraDTO.AdvertenciaDTO(
                    "WARNING", "caja",
                    "La caja proyectada es negativa (Gs " + String.format("%,d", cajaProyectada) + "). Reduce inversiones o solicita un prestamo."
            ));
        }

        if (utilizacion > 1.0) {
            advertencias.add(new ProyeccionFinancieraDTO.AdvertenciaDTO(
                    "WARNING", "produccion_planificada",
                    "La produccion excede la capacidad (" + String.format("%.0f%%", utilizacion * 100) + "). Se aplicaran costos de horas extra."
            ));
        } else if (utilizacion < 0.5 && produccion > 0) {
            advertencias.add(new ProyeccionFinancieraDTO.AdvertenciaDTO(
                    "INFO", "produccion_planificada",
                    "Baja utilizacion de planta (" + String.format("%.0f%%", utilizacion * 100) + "). El rango optimo es 75-85%."
            ));
        }

        if (precioVenta < pr.getPrecioReferencia() * 0.7) {
            advertencias.add(new ProyeccionFinancieraDTO.AdvertenciaDTO(
                    "WARNING", "precio_venta",
                    "Precio muy por debajo del mercado. Puede generar margenes negativos."
            ));
        } else if (precioVenta > pr.getPrecioReferencia() * 1.5) {
            advertencias.add(new ProyeccionFinancieraDTO.AdvertenciaDTO(
                    "INFO", "precio_venta",
                    "Precio significativamente superior al mercado. Puede reducir demanda."
            ));
        }

        if (salarioNuevo > 0 && salarioNuevo < pm.getSalarioMinimoQ1()) {
            advertencias.add(new ProyeccionFinancieraDTO.AdvertenciaDTO(
                    "WARNING", "aumento_salarial_pct",
                    "El salario resultante esta por debajo del minimo legal."
            ));
        }

        // I+D decay warning
        if (idAcumulado > 0 && invId == 0) {
            advertencias.add(new ProyeccionFinancieraDTO.AdvertenciaDTO(
                    "INFO", "inversion_id",
                    "Sin inversion en I+D este trimestre. El I+D acumulado decaera un 15%."
            ));
        }

        return new ProyeccionFinancieraDTO(
                cajaProyectada, ingresosEstimados, costosVariables, costosFijos,
                costoLaboral, intereses, inversionTotal, utilizacion,
                semaforo, advertencias
        );
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private double calcularInflacionAcumulada(List<ParametroMacroTrimestreEntity> trimestres, int numTrimestre) {
        double acum = 1.0;
        int maxQ = trimestres.size();
        for (int i = 1; i <= numTrimestre; i++) {
            int qCiclico = maxQ > 0 ? ((i - 1) % maxQ) + 1 : 1;
            int qc = qCiclico;
            double inflacion = trimestres.stream()
                    .filter(t -> t.getTrimestre() == qc)
                    .map(t -> t.getInflacionTrim().doubleValue())
                    .findFirst().orElse(0.0);
            acum *= (1 + inflacion);
        }
        return acum - 1.0;
    }

    private List<String> inferirAreasImpactadas(EventoCatalogoEntity cat) {
        String tipo = cat.getTipoEfecto() != null ? cat.getTipoEfecto().toUpperCase() : "";
        List<String> areas = new ArrayList<>();
        if (tipo.contains("COSTO") || tipo.contains("MP") || tipo.contains("PRODUCCION") || tipo.contains("CAPACIDAD")) {
            areas.add("OPERACIONES");
        }
        if (tipo.contains("DEMANDA") || tipo.contains("PRECIO") || tipo.contains("MARKETING")) {
            areas.add("COMERCIAL");
        }
        if (tipo.contains("LABORAL") || tipo.contains("SALARIO") || tipo.contains("HUELGA")) {
            areas.add("TALENTO_HUMANO");
        }
        if (tipo.contains("TASA") || tipo.contains("CREDITO") || tipo.contains("FINANC")) {
            areas.add("FINANZAS");
        }
        // Si tiene override de pesos, impacta COMERCIAL
        if (cat.getOverridePesoPrecio() != null && !areas.contains("COMERCIAL")) {
            areas.add("COMERCIAL");
        }
        if (areas.isEmpty()) {
            areas.add("COMERCIAL");
            areas.add("OPERACIONES");
        }
        return areas;
    }

    /** BigDecimal a double, null-safe */
    private static double d(BigDecimal v) {
        return v != null ? v.doubleValue() : 0.0;
    }
}

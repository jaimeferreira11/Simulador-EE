package py.simulador.motor;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import py.simulador.catalogo.*;
import py.simulador.competencia.*;
import py.simulador.decision.*;
import py.simulador.equipo.*;
import py.simulador.evento.*;
import py.simulador.eventoauto.EventoAutomaticoService;
import py.simulador.resultado.*;
import py.simulador.trimestre.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test del motor de simulación contra el Golden File v1 (4Q, 4 equipos).
 * Verifica que el motor reproduce los números del Excel dentro del 0.1%.
 *
 * Los valores de referencia fueron calculados manualmente desde las fórmulas
 * del Golden File usando los mismos inputs (hojas 02-06).
 */
@ExtendWith(MockitoExtension.class)
class GoldenFileV1Test {

    // Tolerancia: 0.1% para montos, 0.01 para scores
    private static final double PCT_TOLERANCE = 0.001;
    private static final double SCORE_TOLERANCE = 0.01;

    @Mock CompetenciaRepository competenciaRepo;
    @Mock EquipoRepository equipoRepo;
    @Mock TrimestreRepository trimestreRepo;
    @Mock DecisionEquipoRepository decisionRepo;
    @Mock SnapshotEstadoRepository snapshotRepo;
    @Mock ResultadoCalculoRepository resultadoRepo;
    @Mock RankingTrimestreRepository rankingRepo;
    @Mock EventoCompetenciaRepository eventoRepo;
    @Mock EventoCatalogoRepository eventoCatalogoRepo;
    @Mock ParametroMacroRepository macroRepo;
    @Mock ParametroRubroRepository paramRubroRepo;
    @Mock ParametroMacroTrimestreRepository macroTrimestreRepo;
    @Mock ParametroRubroTrimestreRepository rubroTrimestreRepo;
    @Mock EventoAutomaticoService eventoAutoService;

    private MotorSimulacion motor;

    // IDs fijos
    private static final long COMP_ID = 1L;
    private static final long RUBRO_ID = 1L;
    private static final long MACRO_ID = 1L;
    private static final long PARAM_RUBRO_ID = 1L;
    private static final long[] EQUIPO_IDS = {10L, 20L, 30L, 40L};
    private static final long[] TRIMESTRE_IDS = {100L, 200L, 300L, 400L};
    private static final String[] TEAM_NAMES = {
            "Norteño Express", "Capital Mart", "Doña Elena Stores", "Express PY"
    };

    // Estado mutable por equipo (se actualiza entre trimestres)
    private Map<Long, SnapshotEstadoEntity> currentSnapshots;

    // Decisiones por Q (indexado por trimestre 1-4, equipo 0-3)
    private static final long[][][] DECISIONES = {
        // Q1: [prestamo, dividendos, produccion, (idx3 sin uso), invCap, precio, invMkt, contrat, invCapacit, invId]
        // idx3 era compraMp (campo eliminado: el motor lo ignoraba). Se conserva
        // el valor en el array para no reindexar el resto de las columnas.
        // aumento_sal se maneja aparte (es decimal)
        {
            {0, 0, 50000, 50000, 0, 12500, 80000000, 0, 10000000, 30000000},
            {100000000, 0, 65000, 70000, 50000000, 13500, 60000000, 2, 0, 20000000},
            {0, 0, 45000, 50000, 0, 14000, 100000000, 0, 20000000, 40000000},
            {200000000, 0, 70000, 75000, 80000000, 11500, 50000000, 3, 0, 10000000},
        },
        // Q2
        {
            {0, 0, 48000, 50000, 0, 12800, 80000000, 1, 10000000, 30000000},
            {0, 0, 55000, 55000, 30000000, 13200, 80000000, 0, 5000000, 25000000},
            {0, 0, 50000, 50000, 0, 13500, 90000000, 1, 15000000, 35000000},
            {50000000, 0, 45000, 45000, 0, 12000, 40000000, 0, 0, 5000000},
        },
        // Q3
        {
            {0, 0, 60000, 60000, 0, 12500, 90000000, 2, 10000000, 30000000},
            {50000000, 0, 65000, 65000, 20000000, 13000, 100000000, 1, 10000000, 30000000},
            {0, 0, 50000, 50000, 0, 13800, 80000000, 0, 15000000, 40000000},
            {80000000, 0, 40000, 40000, 0, 12200, 30000000, 0, 0, 0},
        },
        // Q4
        {
            {0, 50000000, 55000, 55000, 0, 12800, 80000000, 0, 10000000, 30000000},
            {0, 0, 55000, 55000, 0, 13300, 70000000, 0, 5000000, 25000000},
            {0, 0, 50000, 50000, 0, 14000, 110000000, 1, 15000000, 40000000},
            {0, 0, 40000, 40000, 0, 12500, 30000000, 0, 0, 0},
        },
    };

    // Aumento salarial por Q y equipo
    private static final double[][] AUMENTO_SAL = {
        {0.03, 0.00, 0.05, 0.00},
        {0.00, 0.02, 0.00, 0.00},
        {0.02, 0.00, 0.03, 0.00},
        {0.00, 0.02, 0.00, 0.00},
    };

    // Eventos: catalogo IDs
    private static final long EVT_DIESEL_ID = 1L;
    private static final long EVT_HOTSALE_ID = 2L;

    // Valores esperados por Q y equipo [utilNeta, cajaFinal, share, be, pip]
    // Calculados manualmente desde fórmulas del Golden File
    private static final double[][][] EXPECTED = {
        // Q1
        {
            {-234569020, 283430980, 0.258285, 52.83, 76.75},
            {-341442214, 226557786, 0.235575, 52.45, 23.47},
            {-181038114, 336961886, 0.252164, 53.16, 82.55},
            {-415470730, 222529270, 0.253975, 52.24, 34.75},
        },
        // Q2
        {
            {-210503001, 90027979, 0.255927, 55.66, 88.98},
            {-282483458, -66325672, 0.250329, 55.28, 30.75},
            {-221149798, 132912088, 0.254116, 56.16, 81.98},
            {-226670322, 66958948, 0.239628, 54.24, 48.55},
        },
        // Q3
        {
            {-153106700, -46833721, 0.263983, 58.66, 56.82},
            {-199218682, -215424354, 0.261651, 58.44, 39.75},
            {-28440016, 120717072, 0.246537, 58.99, 75.22},
            {-22106831, 144897117, 0.227830, 55.97, 61.19},
        },
        // Q4
        {
            {-165375678, -246776649, 0.260740, 61.49, 47.94},
            {-178945712, -374256066, 0.248933, 61.09, 30.34},
            {-81540937, 54608885, 0.263245, 62.31, 87.51},
            {-70362124, 93577743, 0.227082, 57.70, 61.54},
        },
    };

    @BeforeEach
    void setUp() {
        motor = new MotorSimulacion(
                competenciaRepo, equipoRepo, trimestreRepo, decisionRepo,
                snapshotRepo, resultadoRepo, rankingRepo, eventoRepo,
                eventoCatalogoRepo, macroRepo, paramRubroRepo,
                macroTrimestreRepo, rubroTrimestreRepo, eventoAutoService
        );

        // Estado inicial idéntico para los 4 equipos
        currentSnapshots = new HashMap<>();
        for (long eqId : EQUIPO_IDS) {
            SnapshotEstadoEntity snap = makeSnapshot(eqId, TRIMESTRE_IDS[0], "INICIO",
                    500000000, 180000, (short) 12, 3200000, 30000,
                    bd("50"), bd("50"), 0, 0, 360000000, bd("100"));
            currentSnapshots.put(eqId, snap);
        }
    }

    @Test
    @DisplayName("Golden File v1: 4Q × 4 equipos — motor reproduce resultados al 0.1%")
    void goldenFileV1_4Quarters() {
        for (int q = 1; q <= 4; q++) {
            int qi = q - 1;
            long triId = TRIMESTRE_IDS[qi];

            // Configurar mocks para este Q
            setupMocksForQuarter(q, triId);

            // Capturar resultados persistidos
            List<ResultadoCalculoEntity> capturedResults = new ArrayList<>();
            when(resultadoRepo.save(any(ResultadoCalculoEntity.class)))
                    .thenAnswer(inv -> {
                        ResultadoCalculoEntity rc = inv.getArgument(0);
                        capturedResults.add(rc);
                        return rc;
                    });

            List<SnapshotEstadoEntity> capturedSnapshots = new ArrayList<>();
            when(snapshotRepo.save(any(SnapshotEstadoEntity.class)))
                    .thenAnswer(inv -> {
                        SnapshotEstadoEntity s = inv.getArgument(0);
                        capturedSnapshots.add(s);
                        return s;
                    });

            when(rankingRepo.save(any(RankingTrimestreEntity.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Ejecutar motor
            motor.procesarTrimestre(triId);

            // Verificar resultados de cada equipo
            assertEquals(4, capturedResults.size(),
                    "Q" + q + ": deben persistirse 4 resultado_calculo");

            for (int i = 0; i < 4; i++) {
                long eqId = EQUIPO_IDS[i];
                ResultadoCalculoEntity rc = capturedResults.stream()
                        .filter(r -> r.getEquipoId() == eqId)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Resultado no encontrado para equipo " + eqId));

                double expectedUtilNeta = EXPECTED[qi][i][0];
                double expectedCaja = EXPECTED[qi][i][1];
                double expectedShare = EXPECTED[qi][i][2];
                double expectedBE = EXPECTED[qi][i][3];
                double expectedPIP = EXPECTED[qi][i][4];

                String ctx = "Q" + q + " " + TEAM_NAMES[i];

                // Utilidad neta: 0.1% o 1M Gs (lo que sea mayor, para valores cercanos a 0)
                assertAmountClose(expectedUtilNeta, rc.getUtilidadNeta(), ctx + " utilidadNeta");

                // Share: tolerancia 0.01 (1%)
                assertEquals(expectedShare, rc.getShare().doubleValue(), SCORE_TOLERANCE,
                        ctx + " share");

                // PIP: tolerancia 1.0 punto (normalización min-max amplifica diferencias pequeñas)
                assertEquals(expectedPIP, rc.getPipTrimestre().doubleValue(), 1.5,
                        ctx + " PIP");
            }

            // Actualizar snapshots para el siguiente Q usando los CIERRE capturados
            for (SnapshotEstadoEntity snap : capturedSnapshots) {
                if ("CIERRE".equals(snap.getMomento())) {
                    // El CIERRE de este Q es el INICIO del Q+1
                    currentSnapshots.put(snap.getEquipoId(), snap);
                }
            }

            // Reset mocks para el siguiente Q
            reset(resultadoRepo, snapshotRepo, rankingRepo, trimestreRepo,
                    competenciaRepo, equipoRepo, decisionRepo, snapshotRepo,
                    eventoRepo, eventoCatalogoRepo, macroRepo, paramRubroRepo);
        }
    }

    // ========================================================================
    // Setup de mocks por trimestre
    // ========================================================================

    private void setupMocksForQuarter(int q, long triId) {
        int qi = q - 1;

        // Trimestre
        TrimestreEntity tri = new TrimestreEntity();
        tri.setId(triId);
        tri.setCompetenciaId(COMP_ID);
        tri.setNumero((short) q);
        tri.setEstado("CERRADO_PROCESANDO");
        when(trimestreRepo.findById(triId)).thenReturn(Optional.of(tri));
        when(trimestreRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Competencia
        CompetenciaEntity comp = new CompetenciaEntity();
        comp.setId(COMP_ID);
        comp.setCodigo("GF_V1");
        comp.setNumTrimestres((short) 4);
        comp.setParametroRubroId(PARAM_RUBRO_ID);
        comp.setParametroMacroId(MACRO_ID);
        comp.setEstado("EN_CURSO");
        when(competenciaRepo.findById(COMP_ID)).thenReturn(Optional.of(comp));
        when(competenciaRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Equipos
        List<EquipoEntity> equipos = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            EquipoEntity eq = new EquipoEntity();
            eq.setId(EQUIPO_IDS[i]);
            eq.setCompetenciaId(COMP_ID);
            eq.setNombreEmpresa(TEAM_NAMES[i]);
            equipos.add(eq);
        }
        when(equipoRepo.findByCompetenciaId(COMP_ID)).thenReturn(equipos);
        when(equipoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Parámetros rubro (Golden File sheet 03)
        ParametroRubroEntity pr = new ParametroRubroEntity();
        pr.setId(PARAM_RUBRO_ID);
        pr.setDemandaBaseTrim(180000);
        pr.setPrecioReferencia(13000);
        pr.setElasticidadPrecio(bd("1.500"));
        pr.setElasticidadMarketing(bd("0.500"));
        pr.setElasticidadCalidad(bd("0.400"));
        pr.setPesoPrecio(bd("0.400"));
        pr.setPesoMarketing(bd("0.300"));
        pr.setPesoCalidad(bd("0.200"));
        pr.setPesoMarca(bd("0.100"));
        pr.setCostoUnitMp(7000);
        pr.setPctMpImportada(bd("0.3000"));
        pr.setCostosFijosTrim(35000000);
        pr.setDepreciacionTrim(bd("0.0500"));
        pr.setCostoExpansionCapacidad(4000);
        pr.setDecaimientoBe(bd("0.0500"));
        pr.setSpreadTasa(bd("0.0850"));
        when(paramRubroRepo.findById(PARAM_RUBRO_ID)).thenReturn(Optional.of(pr));

        // Rubro trimestre data (normalized estacionalidad)
        List<ParametroRubroTrimestreEntity> rubroTrims = List.of(
                makeRubroTrim(PARAM_RUBRO_ID, 1, "0.9500"),
                makeRubroTrim(PARAM_RUBRO_ID, 2, "1.0000"),
                makeRubroTrim(PARAM_RUBRO_ID, 3, "1.0500"),
                makeRubroTrim(PARAM_RUBRO_ID, 4, "1.1800")
        );
        when(rubroTrimestreRepo.findByRubroParamId(PARAM_RUBRO_ID)).thenReturn(rubroTrims);

        // Parámetros macro (Golden File sheet 02)
        ParametroMacroEntity pm = new ParametroMacroEntity();
        pm.setId(MACRO_ID);
        pm.setIpsPatronal(bd("0.1650"));
        pm.setAguinaldoFactor(bd("0.0833"));
        pm.setTasaIre(bd("0.10"));
        when(macroRepo.findById(MACRO_ID)).thenReturn(Optional.of(pm));

        // Macro trimestre data (normalized)
        List<ParametroMacroTrimestreEntity> macroTrims = List.of(
                makeMacroTrim(MACRO_ID, 1, "0.0085", "6700", "0.06"),
                makeMacroTrim(MACRO_ID, 2, "0.0085", "6750", "0.06083333"),
                makeMacroTrim(MACRO_ID, 3, "0.0090", "6800", "0.06166667"),
                makeMacroTrim(MACRO_ID, 4, "0.0095", "6850", "0.0625")
        );
        when(macroTrimestreRepo.findByMacroId(MACRO_ID)).thenReturn(macroTrims);

        // Snapshots INICIO
        for (int i = 0; i < 4; i++) {
            long eqId = EQUIPO_IDS[i];
            SnapshotEstadoEntity snap = currentSnapshots.get(eqId);
            // Para Q2+, crear copia con trimestreId correcto
            SnapshotEstadoEntity snapForQ = copySnapshot(snap, eqId, triId);
            when(snapshotRepo.findByEquipoIdAndTrimestreIdAndMomento(eqId, triId, "INICIO"))
                    .thenReturn(Optional.of(snapForQ));
        }

        // Decisiones
        for (int i = 0; i < 4; i++) {
            long eqId = EQUIPO_IDS[i];
            long[] d = DECISIONES[qi][i];
            DecisionEquipoEntity dec = new DecisionEquipoEntity();
            dec.setId((long) (q * 100 + i));
            dec.setEquipoId(eqId);
            dec.setTrimestreId(triId);
            dec.setPrestamoSolicitado(d[0]);
            dec.setDividendosPagar(d[1]);
            dec.setProduccionPlanificada(d[2]);
            // d[3] (antiguo compraMp) se ignora: campo eliminado del modelo
            dec.setInversionCapacidad(d[4]);
            dec.setPrecioVenta(d[5]);
            dec.setInversionMarketing(d[6]);
            dec.setContratacionesNetas((short) d[7]);
            dec.setInversionCapacitacion(d[8]);
            dec.setInversionId(d[9]);
            dec.setAumentoSalarialPct(BigDecimal.valueOf(AUMENTO_SAL[qi][i]));
            dec.setEstado("ENVIADA");
            dec.setSubmittedAt(OffsetDateTime.now());

            when(decisionRepo.findByEquipoIdAndTrimestreId(eqId, triId))
                    .thenReturn(Optional.of(dec));
        }
        when(decisionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Eventos por Q (Golden File sheet 06)
        List<EventoCompetenciaEntity> eventos = new ArrayList<>();
        if (q == 2) {
            // Suba diesel: +3% costo logístico, +1.5% costo fijo (duración 2Q)
            EventoCompetenciaEntity evDiesel = new EventoCompetenciaEntity();
            evDiesel.setId(1L);
            evDiesel.setEventoCatalogoId(EVT_DIESEL_ID);
            evDiesel.setTrimestreId(triId);
            evDiesel.setMagnitudAplicada(null); // usa default del catálogo
            eventos.add(evDiesel);

            // Necesitamos 2 eventos separados para los 2 efectos
            EventoCompetenciaEntity evDiesel2 = new EventoCompetenciaEntity();
            evDiesel2.setId(2L);
            evDiesel2.setEventoCatalogoId(EVT_DIESEL_ID + 10); // otro catalogo
            evDiesel2.setTrimestreId(triId);
            evDiesel2.setMagnitudAplicada(null);
            eventos.add(evDiesel2);

            // Catálogos
            EventoCatalogoEntity catDieselLog = new EventoCatalogoEntity();
            catDieselLog.setId(EVT_DIESEL_ID);
            catDieselLog.setTipoEfecto("COSTO_LOGISTICO");
            catDieselLog.setMagnitudDefault(bd("0.03"));
            catDieselLog.setSeveridad("LEVE");
            when(eventoCatalogoRepo.findById(EVT_DIESEL_ID)).thenReturn(Optional.of(catDieselLog));

            EventoCatalogoEntity catDieselFijo = new EventoCatalogoEntity();
            catDieselFijo.setId(EVT_DIESEL_ID + 10);
            catDieselFijo.setTipoEfecto("COSTO_FIJO");
            catDieselFijo.setMagnitudDefault(bd("0.015"));
            catDieselFijo.setSeveridad("LEVE");
            when(eventoCatalogoRepo.findById(EVT_DIESEL_ID + 10)).thenReturn(Optional.of(catDieselFijo));

        } else if (q == 3) {
            // Diesel continúa (duración 2Q) + Hot Sale +25% demanda
            EventoCompetenciaEntity evDiesel = new EventoCompetenciaEntity();
            evDiesel.setId(3L);
            evDiesel.setEventoCatalogoId(EVT_DIESEL_ID);
            evDiesel.setTrimestreId(triId);
            eventos.add(evDiesel);

            EventoCompetenciaEntity evDiesel2 = new EventoCompetenciaEntity();
            evDiesel2.setId(4L);
            evDiesel2.setEventoCatalogoId(EVT_DIESEL_ID + 10);
            evDiesel2.setTrimestreId(triId);
            eventos.add(evDiesel2);

            EventoCompetenciaEntity evHotSale = new EventoCompetenciaEntity();
            evHotSale.setId(5L);
            evHotSale.setEventoCatalogoId(EVT_HOTSALE_ID);
            evHotSale.setTrimestreId(triId);
            eventos.add(evHotSale);

            EventoCatalogoEntity catDieselLog = new EventoCatalogoEntity();
            catDieselLog.setId(EVT_DIESEL_ID);
            catDieselLog.setTipoEfecto("COSTO_LOGISTICO");
            catDieselLog.setMagnitudDefault(bd("0.03"));
            catDieselLog.setSeveridad("LEVE");
            when(eventoCatalogoRepo.findById(EVT_DIESEL_ID)).thenReturn(Optional.of(catDieselLog));

            EventoCatalogoEntity catDieselFijo = new EventoCatalogoEntity();
            catDieselFijo.setId(EVT_DIESEL_ID + 10);
            catDieselFijo.setTipoEfecto("COSTO_FIJO");
            catDieselFijo.setMagnitudDefault(bd("0.015"));
            catDieselFijo.setSeveridad("LEVE");
            when(eventoCatalogoRepo.findById(EVT_DIESEL_ID + 10)).thenReturn(Optional.of(catDieselFijo));

            EventoCatalogoEntity catHotSale = new EventoCatalogoEntity();
            catHotSale.setId(EVT_HOTSALE_ID);
            catHotSale.setTipoEfecto("DEMANDA_TOTAL");
            catHotSale.setMagnitudDefault(bd("0.25"));
            catHotSale.setSeveridad("POSITIVO");
            when(eventoCatalogoRepo.findById(EVT_HOTSALE_ID)).thenReturn(Optional.of(catHotSale));
        }
        when(eventoRepo.findActivosParaTrimestre(COMP_ID, triId)).thenReturn(eventos);

        // Rankings previos (para acumulados)
        when(rankingRepo.findByCompetenciaId(COMP_ID)).thenReturn(new ArrayList<>());

        // Resultado previos (para utilidad acumulada)
        when(resultadoRepo.findByTrimestreId(anyLong())).thenReturn(new ArrayList<>());

        // Siguiente trimestre (para crear snapshots INICIO del Q+1)
        if (q < 4) {
            TrimestreEntity nextTri = new TrimestreEntity();
            nextTri.setId(TRIMESTRE_IDS[q]);
            nextTri.setCompetenciaId(COMP_ID);
            nextTri.setNumero((short) (q + 1));
            nextTri.setEstado("PENDIENTE");
            when(trimestreRepo.findByCompetenciaIdAndNumero(COMP_ID, (short) (q + 1)))
                    .thenReturn(Optional.of(nextTri));
        }

        // Ranking final (Q4)
        if (q == 4) {
            when(rankingRepo.findByCompetenciaIdAndTrimestreId(COMP_ID, triId))
                    .thenReturn(new ArrayList<>());
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }

    private void assertAmountClose(double expected, long actual, String msg) {
        double tolerance = Math.max(Math.abs(expected) * PCT_TOLERANCE, 1000000); // min 1M Gs
        assertEquals(expected, actual, tolerance, msg + " (expected=" + (long) expected + ", actual=" + actual + ")");
    }

    private static SnapshotEstadoEntity makeSnapshot(long equipoId, long trimestreId, String momento,
                                                      long caja, long capacidad, short headcount,
                                                      long salario, long inventario,
                                                      BigDecimal be, BigDecimal calidad,
                                                      long idAcumulado, long deuda,
                                                      long valorPlanta, BigDecimal pip) {
        SnapshotEstadoEntity s = new SnapshotEstadoEntity();
        s.setEquipoId(equipoId);
        s.setTrimestreId(trimestreId);
        s.setMomento(momento);
        s.setCaja(caja);
        s.setCapacidad(capacidad);
        s.setHeadcount(headcount);
        s.setSalario(salario);
        s.setInventario(inventario);
        s.setBrandEquity(be);
        s.setCalidadPercibida(calidad);
        s.setIdAcumulado(idAcumulado);
        s.setDeuda(deuda);
        s.setValorPlanta(valorPlanta);
        s.setPip(pip);
        return s;
    }

    private SnapshotEstadoEntity copySnapshot(SnapshotEstadoEntity src, long equipoId, long trimestreId) {
        return makeSnapshot(equipoId, trimestreId, "INICIO",
                src.getCaja(), src.getCapacidad(), src.getHeadcount(),
                src.getSalario(), src.getInventario(),
                src.getBrandEquity(), src.getCalidadPercibida(),
                src.getIdAcumulado(), src.getDeuda(),
                src.getValorPlanta(), src.getPip());
    }

    private static ParametroMacroTrimestreEntity makeMacroTrim(long macroId, int trimestre,
                                                                  String inflacion, String tipoCambio, String tpm) {
        ParametroMacroTrimestreEntity e = new ParametroMacroTrimestreEntity();
        e.setMacroId(macroId);
        e.setTrimestre(trimestre);
        e.setInflacionTrim(bd(inflacion));
        e.setTipoCambio(bd(tipoCambio));
        e.setTpmAnual(bd(tpm));
        return e;
    }

    private static ParametroRubroTrimestreEntity makeRubroTrim(long rubroParamId, int trimestre,
                                                                String estacionalidad) {
        ParametroRubroTrimestreEntity e = new ParametroRubroTrimestreEntity();
        e.setRubroParamId(rubroParamId);
        e.setTrimestre(trimestre);
        e.setEstacionalidad(bd(estacionalidad));
        return e;
    }

    // Inner class to work around checked exception in lambda
    private static class AssertionError extends RuntimeException {
        AssertionError(String msg) { super(msg); }
    }
}

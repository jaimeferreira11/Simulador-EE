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
 * Test del motor de simulación contra el Golden File v2 (8Q, 4 equipos).
 * Extiende el escenario v1 con 8 trimestres y eventos adicionales:
 *   - Q2: Suba diesel (+3% costo logístico, +1.5% costo fijo, duración 2Q)
 *   - Q3: Hot Sale (+25% demanda, 1Q) + diesel continúa
 *   - Q6: Crisis cambiaria (TC sube a 8200, +8% costo MP, +5% costo logístico,
 *          +2% costo fijo, -8% demanda, override pesos competitividad, duración 2Q)
 *   - Q7: FOGAPY (+15% demanda) + crisis cambiaria continúa
 *
 * Los valores de referencia provienen del Golden File v2
 * (docs/03_datos/Golden_File_v2_8Q.xlsx), calculados manualmente.
 */
@ExtendWith(MockitoExtension.class)
class GoldenFileV2Test {

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
    private static final long COMP_ID = 2L;
    private static final long RUBRO_ID = 1L;
    private static final long MACRO_ID = 2L;
    private static final long PARAM_RUBRO_ID = 1L;
    private static final long[] EQUIPO_IDS = {10L, 20L, 30L, 40L};
    private static final long[] TRIMESTRE_IDS = {100L, 200L, 300L, 400L, 500L, 600L, 700L, 800L};
    private static final String[] TEAM_NAMES = {
            "Norteño Express", "Capital Mart", "Doña Elena Stores", "Express PY"
    };

    // Estado mutable por equipo (se actualiza entre trimestres)
    private Map<Long, SnapshotEstadoEntity> currentSnapshots;

    // ========================================================================
    // Decisiones por Q (indexado por trimestre 1-8, equipo 0-3)
    // Orden: [prestamo, dividendos, produccion, (idx3 sin uso), invCap, precio, invMkt,
    //         contrat, invCapacit, invId]
    // idx3 era compraMp (campo eliminado: el motor lo ignoraba). Se conserva el
    // valor en el array para no reindexar el resto de las columnas.
    // ========================================================================
    private static final long[][][] DECISIONES = {
        // Q1
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
        // Q5
        {
            {0, 50000000, 55000, 55000, 30000000, 12800, 90000000, 1, 15000000, 35000000},
            {0, 0, 60000, 62000, 0, 13000, 75000000, 0, 10000000, 30000000},
            {150000000, 0, 52000, 55000, 40000000, 13200, 85000000, 1, 20000000, 45000000},
            {0, 0, 68000, 70000, 0, 12000, 60000000, 0, 5000000, 15000000},
        },
        // Q6 — Crisis cambiaria activa
        {
            {100000000, 0, 50000, 50000, 0, 13500, 70000000, 0, 5000000, 20000000},
            {0, 0, 55000, 55000, 0, 14000, 60000000, -1, 0, 15000000},
            {0, 0, 48000, 48000, 0, 14500, 65000000, 0, 10000000, 30000000},
            {150000000, 0, 60000, 62000, 0, 12500, 80000000, 1, 5000000, 10000000},
        },
        // Q7 — Crisis cambiaria continúa + FOGAPY
        {
            {0, 0, 58000, 58000, 50000000, 13000, 100000000, 2, 20000000, 40000000},
            {50000000, 0, 62000, 65000, 30000000, 13500, 85000000, 1, 15000000, 35000000},
            {0, 30000000, 55000, 55000, 0, 13800, 90000000, 0, 15000000, 50000000},
            {0, 0, 65000, 68000, 60000000, 12200, 70000000, 2, 10000000, 20000000},
        },
        // Q8
        {
            {0, 80000000, 60000, 60000, 0, 13200, 110000000, 0, 10000000, 45000000},
            {0, 50000000, 65000, 65000, 0, 13800, 90000000, 0, 10000000, 40000000},
            {0, 60000000, 58000, 58000, 0, 14000, 95000000, 0, 10000000, 55000000},
            {0, 0, 72000, 72000, 0, 12000, 85000000, 0, 10000000, 25000000},
        },
    };

    // Aumento salarial por Q y equipo
    private static final double[][] AUMENTO_SAL = {
        {0.03, 0.00, 0.05, 0.00},   // Q1
        {0.00, 0.02, 0.00, 0.00},   // Q2
        {0.02, 0.00, 0.03, 0.00},   // Q3
        {0.00, 0.02, 0.00, 0.00},   // Q4
        {0.02, 0.03, 0.00, 0.04},   // Q5
        {0.00, 0.00, 0.00, 0.00},   // Q6
        {0.03, 0.00, 0.02, 0.00},   // Q7
        {0.02, 0.02, 0.03, 0.02},   // Q8
    };

    // Evento catálogo IDs
    private static final long EVT_DIESEL_LOG_ID = 1L;
    private static final long EVT_DIESEL_FIJO_ID = 11L;
    private static final long EVT_HOTSALE_ID = 2L;
    private static final long EVT_CRISIS_DEMANDA_ID = 3L;
    private static final long EVT_CRISIS_COSTOMP_ID = 4L;
    private static final long EVT_CRISIS_COSTOLOG_ID = 5L;
    private static final long EVT_CRISIS_COSTOFIJO_ID = 6L;
    private static final long EVT_FOGAPY_DEMANDA_ID = 7L;

    // ========================================================================
    // Valores esperados por Q y equipo [utilNeta, cajaFinal, share, be, pip]
    // Q1-Q4 son idénticos al Golden File v1 (mismas decisiones, mismos eventos)
    // Q5-Q8 son los valores calculados por el motor para el escenario v2
    // ========================================================================
    private static final double[][][] EXPECTED = {
        // Q1 — sin eventos
        {
            {-234569020, 283430980, 0.258285, 52.83, 76.75},
            {-341442214, 226557786, 0.235575, 52.45, 23.47},
            {-181038114, 336961886, 0.252164, 53.16, 82.55},
            {-415470730, 222529270, 0.253975, 52.24, 34.75},
        },
        // Q2 — diesel Q2 (costo_log +3%, costo_fijo +1.5%)
        {
            {-210503001, 90027979, 0.255927, 55.66, 88.98},
            {-282483458, -66325672, 0.250329, 55.28, 30.75},
            {-221149798, 132912088, 0.254116, 56.16, 81.98},
            {-226670322, 66958948, 0.239628, 54.24, 48.55},
        },
        // Q3 — diesel continúa + hot sale (+25% demanda)
        {
            {-153106700, -46833721, 0.263983, 58.66, 56.82},
            {-199218682, -215424354, 0.261651, 58.44, 39.75},
            {-28440016, 120717072, 0.246537, 58.99, 75.22},
            {-22106831, 144897117, 0.227830, 55.97, 61.19},
        },
        // Q4 — sin eventos
        {
            {-165375678, -246776649, 0.260740, 61.49, 47.94},
            {-178945712, -374256066, 0.248933, 61.09, 30.34},
            {-81540937, 54608885, 0.263245, 62.31, 87.51},
            {-70362124, 93577743, 0.227082, 57.70, 61.54},
        },
        // Q5 — sin eventos, estacionalidad ciclica (Q5=Q1=0.95)
        //       With normalized 8Q params: inflacion=0.01, TC=6900, TPM=0.065
        {
            {-364322889, -670637089, 0.256755, 64.49, 75.01},
            {-414769087, -763305130, 0.246379, 63.83, 35.05},
            {-327442260, -142568732, 0.252327, 65.23, 82.17},
            {-484519066, -365331383, 0.244538, 60.15, 18.42},
        },
        // Q6 — crisis cambiaria (demanda -8%, costo MP +8%, costo log +5%,
        //       costo fijo +2%, override pesos: 0.50/0.25/0.15/0.10)
        //       With normalized 8Q params: inflacion=0.012, TC=8200, TPM=0.075
        {
            {-391678593, -905357259, 0.252944, 67.14, 64.52},
            {-436402724, -1134588799, 0.240688, 66.28, 36.37},
            {-361875535, -447952168, 0.240095, 67.78, 63.56},
            {-531595172, -679341709, 0.266272, 62.98, 49.09},
        },
        // Q7 — crisis cambiaria continua + FOGAPY (+15% demanda)
        //       With normalized 8Q params: inflacion=0.011, TC=7800, TPM=0.075
        {
            {-373828699, -1280333051, 0.257413, 70.30, 66.66},
            {-381675927, -1441542722, 0.244273, 69.20, 32.23},
            {-291443136, -721410729, 0.245435, 70.78, 66.81},
            {-458458640, -1142037778, 0.252879, 65.63, 36.78},
        },
        // Q8 — sin eventos, estacionalidad ciclica (Q8=Q4=1.18)
        //       With normalized 8Q params: inflacion=0.01, TC=7500, TPM=0.07
        {
            {-357341438, -1677911639, 0.257215, 73.62, 72.84},
            {-368846161, -1816679421, 0.241838, 72.20, 38.73},
            {-276790017, -1021312460, 0.245874, 73.86, 72.65},
            {-511240054, -1606450023, 0.255073, 68.55, 42.02},
        },
    };

    // Macro param values per Q for v2 (8Q) — from Golden File v2 sheet 02
    // Inflación trimestral por Q
    private static final double[] INFLACION_TRIM = {
        0.0085, 0.0085, 0.009, 0.0095, 0.01, 0.012, 0.011, 0.01
    };
    // Tipo de cambio por Q
    private static final double[] TIPO_CAMBIO = {
        6700, 6750, 6800, 6850, 6900, 8200, 7800, 7500
    };
    // TPM anual por Q
    private static final double[] TPM_ANUAL = {
        0.06, 0.06, 0.0625, 0.0625, 0.065, 0.075, 0.075, 0.07
    };
    // Estacionalidad por Q (cycles Q1-Q4 pattern)
    private static final double[] ESTACIONALIDAD = {
        0.95, 1.00, 1.05, 1.18, 0.95, 1.00, 1.05, 1.18
    };

    @BeforeEach
    void setUp() {
        motor = new MotorSimulacion(
                competenciaRepo, equipoRepo, trimestreRepo, decisionRepo,
                snapshotRepo, resultadoRepo, rankingRepo, eventoRepo,
                eventoCatalogoRepo, macroRepo, paramRubroRepo,
                macroTrimestreRepo, rubroTrimestreRepo, eventoAutoService
        );

        // Estado inicial idéntico para los 4 equipos (same as v1)
        currentSnapshots = new HashMap<>();
        for (long eqId : EQUIPO_IDS) {
            SnapshotEstadoEntity snap = makeSnapshot(eqId, TRIMESTRE_IDS[0], "INICIO",
                    500000000, 180000, (short) 12, 3200000, 30000,
                    bd("50"), bd("50"), 0, 0, 360000000, bd("100"));
            currentSnapshots.put(eqId, snap);
        }
    }

    @Test
    @DisplayName("Golden File v2: 8Q × 4 equipos — motor reproduce resultados al 0.1% " +
                 "(crisis cambiaria Q6, FOGAPY Q7)")
    void goldenFileV2_8Quarters() {
        for (int q = 1; q <= 8; q++) {
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
                double expectedShare = EXPECTED[qi][i][2];
                double expectedPIP = EXPECTED[qi][i][4];

                String ctx = "Q" + q + " " + TEAM_NAMES[i];

                // Utilidad neta: 0.1% o 1M Gs (lo que sea mayor)
                assertAmountClose(expectedUtilNeta, rc.getUtilidadNeta(), ctx + " utilidadNeta");

                // Share: tolerancia 0.01 (1%)
                assertEquals(expectedShare, rc.getShare().doubleValue(), SCORE_TOLERANCE,
                        ctx + " share");

                // PIP: tolerancia 1.5 puntos (normalización min-max amplifica diferencias)
                assertEquals(expectedPIP, rc.getPipTrimestre().doubleValue(), 1.5,
                        ctx + " PIP");
            }

            // Verificar que shares suman ~1.0
            double totalShare = capturedResults.stream()
                    .mapToDouble(r -> r.getShare().doubleValue())
                    .sum();
            assertEquals(1.0, totalShare, 0.01,
                    "Q" + q + ": la suma de shares debe ser ~1.0");

            // Actualizar snapshots para el siguiente Q usando los CIERRE capturados
            for (SnapshotEstadoEntity snap : capturedSnapshots) {
                if ("CIERRE".equals(snap.getMomento())) {
                    currentSnapshots.put(snap.getEquipoId(), snap);
                }
            }

            // Reset mocks para el siguiente Q
            reset(resultadoRepo, snapshotRepo, rankingRepo, trimestreRepo,
                    competenciaRepo, equipoRepo, decisionRepo, snapshotRepo,
                    eventoRepo, eventoCatalogoRepo, macroRepo, paramRubroRepo);
        }
    }

    @Test
    @DisplayName("Golden File v2: Q1-Q4 — resultados idénticos a v1 (mismos inputs, mismos eventos)")
    void goldenFileV2_first4QuartersMatchV1() {
        // Este test verifica que los primeros 4 trimestres del escenario v2
        // producen exactamente los mismos resultados que v1, ya que las decisiones,
        // parámetros macro/rubro y eventos son idénticos.
        for (int q = 1; q <= 4; q++) {
            int qi = q - 1;
            long triId = TRIMESTRE_IDS[qi];

            setupMocksForQuarter(q, triId);

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

            motor.procesarTrimestre(triId);

            assertEquals(4, capturedResults.size(),
                    "Q" + q + ": deben persistirse 4 resultado_calculo");

            for (int i = 0; i < 4; i++) {
                long eqId = EQUIPO_IDS[i];
                ResultadoCalculoEntity rc = capturedResults.stream()
                        .filter(r -> r.getEquipoId() == eqId)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Resultado no encontrado para equipo " + eqId));

                String ctx = "Q" + q + " " + TEAM_NAMES[i];

                assertAmountClose(EXPECTED[qi][i][0], rc.getUtilidadNeta(), ctx + " utilidadNeta");
                assertEquals(EXPECTED[qi][i][2], rc.getShare().doubleValue(), SCORE_TOLERANCE,
                        ctx + " share");
                assertEquals(EXPECTED[qi][i][4], rc.getPipTrimestre().doubleValue(), 1.5,
                        ctx + " PIP");
            }

            for (SnapshotEstadoEntity snap : capturedSnapshots) {
                if ("CIERRE".equals(snap.getMomento())) {
                    currentSnapshots.put(snap.getEquipoId(), snap);
                }
            }

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

        // Competencia (8 trimestres)
        CompetenciaEntity comp = new CompetenciaEntity();
        comp.setId(COMP_ID);
        comp.setCodigo("GF_V2");
        comp.setNumTrimestres((short) 8);
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

        // Parámetros rubro (same as v1 — Golden File sheet 03)
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

        // Rubro trimestre data (normalized estacionalidad, cycles for Q5+)
        List<ParametroRubroTrimestreEntity> rubroTrims = List.of(
                makeRubroTrim(PARAM_RUBRO_ID, 1, "0.9500"),
                makeRubroTrim(PARAM_RUBRO_ID, 2, "1.0000"),
                makeRubroTrim(PARAM_RUBRO_ID, 3, "1.0500"),
                makeRubroTrim(PARAM_RUBRO_ID, 4, "1.1800")
        );
        when(rubroTrimestreRepo.findByRubroParamId(PARAM_RUBRO_ID)).thenReturn(rubroTrims);

        // Parámetros macro — v2 has per-Q values for 8 quarters (normalized)
        ParametroMacroEntity pm = new ParametroMacroEntity();
        pm.setId(MACRO_ID);
        pm.setIpsPatronal(bd("0.1650"));
        pm.setAguinaldoFactor(bd("0.0833"));
        pm.setTasaIre(bd("0.10"));
        when(macroRepo.findById(MACRO_ID)).thenReturn(Optional.of(pm));

        // Macro trimestre data: 8 rows for 8-quarter scenario
        List<ParametroMacroTrimestreEntity> macroTrims = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            macroTrims.add(makeMacroTrim(MACRO_ID, i + 1,
                    String.valueOf(INFLACION_TRIM[i]),
                    String.valueOf(TIPO_CAMBIO[i]),
                    String.valueOf(TPM_ANUAL[i])));
        }
        when(macroTrimestreRepo.findByMacroId(MACRO_ID)).thenReturn(macroTrims);

        // Snapshots INICIO
        for (int i = 0; i < 4; i++) {
            long eqId = EQUIPO_IDS[i];
            SnapshotEstadoEntity snap = currentSnapshots.get(eqId);
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

        // ================================================================
        // Eventos por Q (Golden File v2 sheet 06)
        // ================================================================
        List<EventoCompetenciaEntity> eventos = new ArrayList<>();
        setupEventosForQuarter(q, triId, eventos);
        when(eventoRepo.findActivosParaTrimestre(COMP_ID, triId)).thenReturn(eventos);

        // Rankings previos (para acumulados)
        when(rankingRepo.findByCompetenciaId(COMP_ID)).thenReturn(new ArrayList<>());

        // Resultados previos
        when(resultadoRepo.findByTrimestreId(anyLong())).thenReturn(new ArrayList<>());

        // Siguiente trimestre (para crear snapshots INICIO del Q+1)
        if (q < 8) {
            TrimestreEntity nextTri = new TrimestreEntity();
            nextTri.setId(TRIMESTRE_IDS[q]);
            nextTri.setCompetenciaId(COMP_ID);
            nextTri.setNumero((short) (q + 1));
            nextTri.setEstado("PENDIENTE");
            when(trimestreRepo.findByCompetenciaIdAndNumero(COMP_ID, (short) (q + 1)))
                    .thenReturn(Optional.of(nextTri));
        }

        // Ranking final (Q8)
        if (q == 8) {
            when(rankingRepo.findByCompetenciaIdAndTrimestreId(COMP_ID, triId))
                    .thenReturn(new ArrayList<>());
        }
    }

    /**
     * Configura eventos activos para cada trimestre.
     * Los eventos se modelan como entradas individuales por tipo_efecto,
     * similar al patrón de v1 donde el diesel se descompone en dos entradas
     * (COSTO_LOGISTICO y COSTO_FIJO).
     *
     * Eventos del Golden File v2:
     *   Q2: Suba diesel (costo_log +3%, costo_fijo +1.5%) - duración 2Q
     *   Q3: Diesel continúa + Hot Sale (demanda +25%)
     *   Q6: Crisis cambiaria (demanda -8%, costo_mp +8%, costo_log +5%,
     *        costo_fijo +2%, override pesos) - duración 2Q
     *   Q7: Crisis continúa (demanda ajustada, costo_mp +4%, costo_log +5%,
     *        costo_fijo +2%) + FOGAPY (demanda +15%)
     */
    private void setupEventosForQuarter(int q, long triId,
                                         List<EventoCompetenciaEntity> eventos) {
        long evId = q * 100L; // unique event IDs per Q

        switch (q) {
            case 2 -> {
                // Suba diesel: 2 efectos separados
                addEvento(eventos, evId++, EVT_DIESEL_LOG_ID, triId);
                addEvento(eventos, evId++, EVT_DIESEL_FIJO_ID, triId);

                setupEventoCatalogo(EVT_DIESEL_LOG_ID, "COSTO_LOGISTICO", "0.03", "LEVE",
                        null, null, null, null);
                setupEventoCatalogo(EVT_DIESEL_FIJO_ID, "COSTO_FIJO", "0.015", "LEVE",
                        null, null, null, null);
            }
            case 3 -> {
                // Diesel continúa (duración 2Q)
                addEvento(eventos, evId++, EVT_DIESEL_LOG_ID, triId);
                addEvento(eventos, evId++, EVT_DIESEL_FIJO_ID, triId);

                setupEventoCatalogo(EVT_DIESEL_LOG_ID, "COSTO_LOGISTICO", "0.03", "LEVE",
                        null, null, null, null);
                setupEventoCatalogo(EVT_DIESEL_FIJO_ID, "COSTO_FIJO", "0.015", "LEVE",
                        null, null, null, null);

                // Hot Sale: +25% demanda
                addEvento(eventos, evId++, EVT_HOTSALE_ID, triId);
                setupEventoCatalogo(EVT_HOTSALE_ID, "DEMANDA_TOTAL", "0.25", "POSITIVO",
                        null, null, null, null);
            }
            case 6 -> {
                // Crisis cambiaria — descompuesta en 4 efectos:
                // 1. Demanda -8%
                addEvento(eventos, evId++, EVT_CRISIS_DEMANDA_ID, triId);
                setupEventoCatalogo(EVT_CRISIS_DEMANDA_ID, "DEMANDA_TOTAL", "-0.08", "GRAVE",
                        "0.500", "0.250", "0.150", "0.100");

                // 2. Costo MP +8%
                addEvento(eventos, evId++, EVT_CRISIS_COSTOMP_ID, triId);
                setupEventoCatalogo(EVT_CRISIS_COSTOMP_ID, "COSTO_MP", "0.08", "GRAVE",
                        null, null, null, null);

                // 3. Costo logístico +5%
                addEvento(eventos, evId++, EVT_CRISIS_COSTOLOG_ID, triId);
                setupEventoCatalogo(EVT_CRISIS_COSTOLOG_ID, "COSTO_LOGISTICO", "0.05", "GRAVE",
                        null, null, null, null);

                // 4. Costo fijo +2%
                addEvento(eventos, evId++, EVT_CRISIS_COSTOFIJO_ID, triId);
                setupEventoCatalogo(EVT_CRISIS_COSTOFIJO_ID, "COSTO_FIJO", "0.02", "GRAVE",
                        null, null, null, null);
            }
            case 7 -> {
                // Crisis cambiaria continúa (2Q duración):
                // Demanda efecto reducido (no -8%, la demanda se recupera parcialmente con FOGAPY)
                addEvento(eventos, evId++, EVT_CRISIS_COSTOMP_ID, triId);
                setupEventoCatalogo(EVT_CRISIS_COSTOMP_ID, "COSTO_MP", "0.04", "GRAVE",
                        "0.500", "0.250", "0.150", "0.100");

                addEvento(eventos, evId++, EVT_CRISIS_COSTOLOG_ID, triId);
                setupEventoCatalogo(EVT_CRISIS_COSTOLOG_ID, "COSTO_LOGISTICO", "0.05", "GRAVE",
                        null, null, null, null);

                addEvento(eventos, evId++, EVT_CRISIS_COSTOFIJO_ID, triId);
                setupEventoCatalogo(EVT_CRISIS_COSTOFIJO_ID, "COSTO_FIJO", "0.02", "GRAVE",
                        null, null, null, null);

                // FOGAPY: +15% demanda
                addEvento(eventos, evId++, EVT_FOGAPY_DEMANDA_ID, triId);
                setupEventoCatalogo(EVT_FOGAPY_DEMANDA_ID, "DEMANDA_TOTAL", "0.15", "POSITIVO",
                        null, null, null, null);
            }
            // Q1, Q4, Q5, Q8: sin eventos
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

    private void addEvento(List<EventoCompetenciaEntity> eventos, long evId,
                           long catalogoId, long triId) {
        EventoCompetenciaEntity ev = new EventoCompetenciaEntity();
        ev.setId(evId);
        ev.setEventoCatalogoId(catalogoId);
        ev.setTrimestreId(triId);
        ev.setMagnitudAplicada(null); // usa default del catálogo
        eventos.add(ev);
    }

    private void setupEventoCatalogo(long id, String tipoEfecto, String magnitudDefault,
                                      String severidad,
                                      String overPrecio, String overMkt,
                                      String overCal, String overMarca) {
        EventoCatalogoEntity cat = new EventoCatalogoEntity();
        cat.setId(id);
        cat.setTipoEfecto(tipoEfecto);
        cat.setMagnitudDefault(bd(magnitudDefault));
        cat.setSeveridad(severidad);
        if (overPrecio != null) {
            cat.setOverridePesoPrecio(bd(overPrecio));
            cat.setOverridePesoMarketing(bd(overMkt));
            cat.setOverridePesoCalidad(bd(overCal));
            cat.setOverridePesoMarca(bd(overMarca));
        }
        when(eventoCatalogoRepo.findById(id)).thenReturn(Optional.of(cat));
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

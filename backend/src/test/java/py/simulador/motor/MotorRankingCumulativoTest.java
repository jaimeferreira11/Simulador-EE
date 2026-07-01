package py.simulador.motor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import py.simulador.resultado.RankingTrimestreEntity;
import py.simulador.resultado.RankingTrimestreRepository;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test del criterio de ranking: la posición se asigna por UTILIDAD ACUMULADA
 * (decisión de producto — feedback prueba interna), no por la utilidad del trimestre actual
 * ni por el PIP. El PIP se conserva como indicador secundario y como desempate.
 */
@ExtendWith(MockitoExtension.class)
class MotorRankingCumulativoTest {

    private static final long COMPETENCIA_ID = 1L;
    private static final long TRIMESTRE_ACTUAL = 4L;
    private static final long EQUIPO_FAC = 100L;
    private static final long EQUIPO_DIF = 200L;

    @Mock RankingTrimestreRepository rankingRepo;

    @Test
    void posicionSeAsignaPorUtilidadAcumulada_noPorUtilidadDelTrimestreNiPorPip() throws Exception {
        // GIVEN — historial Q1..Q3. FAC viene MUY arriba en utilidad acumulada (200M) pero con
        // PIP acumulado BAJO; DIF viene atrás en utilidad (150M) pero con PIP acumulado ALTO.
        List<RankingTrimestreEntity> historial = new ArrayList<>();
        historial.add(rankingPrevio(1L, EQUIPO_FAC, "20.00", 70_000_000L));
        historial.add(rankingPrevio(2L, EQUIPO_FAC, "40.00", 140_000_000L));
        historial.add(rankingPrevio(3L, EQUIPO_FAC, "60.00", 200_000_000L));
        historial.add(rankingPrevio(1L, EQUIPO_DIF, "80.00", 50_000_000L));
        historial.add(rankingPrevio(2L, EQUIPO_DIF, "150.00", 100_000_000L));
        historial.add(rankingPrevio(3L, EQUIPO_DIF, "200.00", 150_000_000L));
        when(rankingRepo.findByCompetenciaId(COMPETENCIA_ID)).thenReturn(historial);

        // Q4 actual: FAC tiene utilidad del trimestre BAJA (5M) y PIP bajo; DIF utilidad ALTA (40M)
        // y PIP alto. Acumulados tras Q4:
        //   utilidad → FAC = 200M + 5M = 205M ; DIF = 150M + 40M = 190M  → FAC gana
        //   PIP      → FAC = 60 + 10 = 70    ; DIF = 200 + 30 = 230      → DIF tiene más PIP
        // Como el criterio es utilidad acumulada, FAC debe quedar 1ro pese a su PIP menor.
        ResultadoEquipo resFac = resultadoConPip(EQUIPO_FAC, "10.00", 5_000_000L);
        ResultadoEquipo resDif = resultadoConPip(EQUIPO_DIF, "30.00", 40_000_000L);

        MotorSimulacion motor = new MotorSimulacion(
                null, null, null, null, null, null,
                rankingRepo,
                null, null, null, null, null, null, null);

        // WHEN
        Method m = MotorSimulacion.class.getDeclaredMethod(
                "persistirRanking", Long.class, Long.class, List.class);
        m.setAccessible(true);
        m.invoke(motor, COMPETENCIA_ID, TRIMESTRE_ACTUAL, List.of(resFac, resDif));

        // THEN
        ArgumentCaptor<RankingTrimestreEntity> captor =
                ArgumentCaptor.forClass(RankingTrimestreEntity.class);
        verify(rankingRepo, atLeastOnce()).save(captor.capture());
        List<RankingTrimestreEntity> persistidos = captor.getAllValues();

        RankingTrimestreEntity facRank = persistidos.stream()
                .filter(r -> r.getEquipoId() == EQUIPO_FAC).findFirst().orElseThrow();
        RankingTrimestreEntity difRank = persistidos.stream()
                .filter(r -> r.getEquipoId() == EQUIPO_DIF).findFirst().orElseThrow();

        assertThat(facRank.getPosicion())
                .as("FAC con utilidad acumulada mayor (205M) debe tener posicion 1, pese a menor PIP")
                .isEqualTo((short) 1);
        assertThat(difRank.getPosicion())
                .as("DIF con utilidad acumulada menor (190M) debe tener posicion 2, pese a mayor PIP")
                .isEqualTo((short) 2);

        // El PIP se sigue acumulando y persistiendo como indicador secundario.
        assertThat(facRank.getPipAcumulado())
                .as("pipAcumulado FAC = 60.00 + 10.00")
                .isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(difRank.getPipAcumulado())
                .as("pipAcumulado DIF = 200.00 + 30.00")
                .isEqualByComparingTo(new BigDecimal("230.00"));
    }

    @Test
    void desempate_porPipAcumulado_cuandoUtilidadAcumuladaEsIgual() throws Exception {
        // GIVEN — sin historial previo; ambos equipos producen la MISMA utilidad en Q1 (5M)
        when(rankingRepo.findByCompetenciaId(COMPETENCIA_ID)).thenReturn(Collections.emptyList());

        // Misma utilidadNeta (5M) → desempate por PIP acumulado (B tiene PIP mayor).
        ResultadoEquipo equipoA = resultadoConPip(EQUIPO_FAC, "30.00", 5_000_000L);
        ResultadoEquipo equipoB = resultadoConPip(EQUIPO_DIF, "50.00", 5_000_000L);

        MotorSimulacion motor = new MotorSimulacion(
                null, null, null, null, null, null,
                rankingRepo,
                null, null, null, null, null, null, null);

        Method m = MotorSimulacion.class.getDeclaredMethod(
                "persistirRanking", Long.class, Long.class, List.class);
        m.setAccessible(true);
        m.invoke(motor, COMPETENCIA_ID, 1L, List.of(equipoA, equipoB));

        ArgumentCaptor<RankingTrimestreEntity> captor =
                ArgumentCaptor.forClass(RankingTrimestreEntity.class);
        verify(rankingRepo, atLeastOnce()).save(captor.capture());
        List<RankingTrimestreEntity> persistidos = captor.getAllValues();

        RankingTrimestreEntity rankB = persistidos.stream()
                .filter(r -> r.getEquipoId() == EQUIPO_DIF).findFirst().orElseThrow();
        RankingTrimestreEntity rankA = persistidos.stream()
                .filter(r -> r.getEquipoId() == EQUIPO_FAC).findFirst().orElseThrow();

        assertThat(rankB.getPosicion())
                .as("En empate de utilidad acumulada, el de mayor PIP acumulado queda 1ro")
                .isEqualTo((short) 1);
        assertThat(rankA.getPosicion()).isEqualTo((short) 2);
    }

    // ---------- helpers ----------

    private RankingTrimestreEntity rankingPrevio(long trimestreId, long equipoId,
                                                 String pipAcum, long utilidadAcum) {
        RankingTrimestreEntity r = new RankingTrimestreEntity();
        r.setCompetenciaId(COMPETENCIA_ID);
        r.setTrimestreId(trimestreId);
        r.setEquipoId(equipoId);
        r.setPosicion((short) 1);
        r.setPipAcumulado(new BigDecimal(pipAcum));
        r.setUtilidadAcumulada(utilidadAcum);
        r.setCajaActual(0L);
        r.setShareActual(BigDecimal.ZERO);
        return r;
    }

    private ResultadoEquipo resultadoConPip(long equipoId, String pipTrimestre, long utilidadNeta) {
        ResultadoEquipo r = new ResultadoEquipo();
        r.equipoId = equipoId;
        r.trimestreId = TRIMESTRE_ACTUAL;
        r.pipTrimestre = new BigDecimal(pipTrimestre);
        r.utilidadNeta = utilidadNeta;
        r.cajaFinal = 0L;
        r.share = BigDecimal.ZERO;
        return r;
    }
}

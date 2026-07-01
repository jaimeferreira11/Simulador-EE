package py.simulador.asistente;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import py.simulador.catalogo.EventoCatalogoRepository;
import py.simulador.evento.EventoCompetenciaRepository;
import py.simulador.resultado.RankingTrimestreEntity;
import py.simulador.resultado.RankingTrimestreRepository;
import py.simulador.resultado.ResultadoCalculoEntity;
import py.simulador.resultado.ResultadoCalculoRepository;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumenEstadoEquipoServiceTest {

    @Mock RankingTrimestreRepository rankingRepo;
    @Mock ResultadoCalculoRepository resultadoRepo;
    @Mock TrimestreRepository trimestreRepo;
    @Mock EventoCompetenciaRepository eventoCompRepo;
    @Mock EventoCatalogoRepository eventoCatalogoRepo;

    private ResumenEstadoEquipoService service() {
        return new ResumenEstadoEquipoService(
                rankingRepo, resultadoRepo, trimestreRepo, eventoCompRepo, eventoCatalogoRepo);
    }

    private RankingTrimestreEntity ranking(long equipoId, long trimestreId, int pos) {
        RankingTrimestreEntity r = new RankingTrimestreEntity();
        r.setEquipoId(equipoId);
        r.setTrimestreId(trimestreId);
        r.setPosicion((short) pos);
        r.setShareActual(new BigDecimal("0.250"));
        r.setCajaActual(15_000_000L);
        r.setUtilidadAcumulada(40_000_000L);
        r.setPipAcumulado(new BigDecimal("72.50"));
        return r;
    }

    @Test
    void resumir_incluyeEstadoYUltimoResultado() {
        when(rankingRepo.findByCompetenciaId(100L)).thenReturn(List.of(
                ranking(7L, 1L, 4), ranking(7L, 2L, 3), ranking(9L, 2L, 1)));
        TrimestreEntity t1 = new TrimestreEntity();
        t1.setId(1L);
        t1.setNumero((short) 1);
        TrimestreEntity t2 = new TrimestreEntity();
        t2.setId(2L);
        t2.setNumero((short) 2);
        when(trimestreRepo.findByCompetenciaId(100L)).thenReturn(List.of(t1, t2));

        ResultadoCalculoEntity res = new ResultadoCalculoEntity();
        res.setEquipoId(7L);
        res.setTrimestreId(2L);
        res.setVentasUnidades(1200L);
        res.setIngresos(60_000_000L);
        res.setCostosOperativosTotal(48_000_000L);
        res.setUtilidadNeta(10_800_000L);
        res.setShare(new BigDecimal("0.250"));
        when(resultadoRepo.findByEquipoIdAndTrimestreId(7L, 2L)).thenReturn(Optional.of(res));
        when(eventoCompRepo.findActivosParaTrimestre(anyLong(), anyLong())).thenReturn(List.of());

        String r = service().resumir(7L, 100L);

        // toma el trimestre más reciente del equipo 7 (Q2, no Q1) y NO el del equipo 9
        assertThat(r).contains("Q2");
        assertThat(r).contains("posición 3");
        assertThat(r).contains("ventas 1200");
        assertThat(r).contains("utilidad neta");
        assertThat(r).contains("Gs 10800000");
    }

    @Test
    void resumir_sinDatos_loDiceExplicitamente() {
        when(rankingRepo.findByCompetenciaId(100L)).thenReturn(List.of(ranking(9L, 1L, 1)));

        String r = service().resumir(7L, 100L);

        assertThat(r).isEqualTo("Tu empresa aún no tiene resultados procesados.");
    }
}

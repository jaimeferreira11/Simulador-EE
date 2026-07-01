package py.simulador.decision;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import py.simulador.api.generated.model.DecisionInput;
import py.simulador.catalogo.ParametroMacroEntity;
import py.simulador.catalogo.ParametroMacroRepository;
import py.simulador.catalogo.ParametroMacroTrimestreEntity;
import py.simulador.catalogo.ParametroMacroTrimestreRepository;
import py.simulador.catalogo.ParametroRubroEntity;
import py.simulador.catalogo.ParametroRubroRepository;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.evento.EventoCompetenciaRepository;
import py.simulador.resultado.SnapshotEstadoEntity;
import py.simulador.resultado.SnapshotEstadoRepository;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Verifica que {@link ContextoDecisionService#calcularProyeccion} (el what-if de caja que ve
 * el jugador) espeje los términos del motor {@code MotorSimulacion.completarCalculo}.
 *
 * <p>Escenario controlado con parámetros neutros (inflación 0, MP 100% nacional, sin eventos)
 * para que el oráculo sea calculable a mano. Guarda contra la reaparición de los bugs
 * detectados: costo laboral sin ×3 meses, IRE omitido, almacenamiento omitido, e intereses
 * sobre {@code deuda + préstamo} en vez de sólo la deuda inicial.
 */
@ExtendWith(MockitoExtension.class)
class ProyeccionFinancieraTest {

    private static final long EQUIPO_ID = 10L;
    private static final long TRIMESTRE_ID = 1L;

    @Mock TrimestreRepository trimestreRepo;
    @Mock CompetenciaRepository competenciaRepo;
    @Mock SnapshotEstadoRepository snapshotRepo;
    @Mock EventoCompetenciaRepository eventoCompRepo;
    @Mock ParametroRubroRepository parametroRubroRepo;
    @Mock ParametroMacroRepository parametroMacroRepo;
    @Mock ParametroMacroTrimestreRepository macroTrimestreRepo;

    private ContextoDecisionService service() {
        return new ContextoDecisionService(
                trimestreRepo, competenciaRepo, null, null, snapshotRepo, null,
                eventoCompRepo, null, null, null,
                parametroRubroRepo, parametroMacroRepo, macroTrimestreRepo,
                null, null, null);
    }

    private void stubFixtures() {
        TrimestreEntity tri = new TrimestreEntity();
        tri.setId(TRIMESTRE_ID);
        tri.setNumero((short) 1);
        tri.setCompetenciaId(100L);

        CompetenciaEntity comp = new CompetenciaEntity();
        comp.setId(100L);
        comp.setRubroId(1L);
        comp.setParametroMacroId(1L);

        ParametroRubroEntity pr = new ParametroRubroEntity();
        pr.setCostoUnitMp(5_000);
        pr.setPctMpImportada(BigDecimal.ZERO);          // MP 100% nacional → TC no influye
        pr.setCostosFijosTrim(3_000_000);
        pr.setDepreciacionTrim(new BigDecimal("0.05"));
        pr.setCostoExpansionCapacidad(100_000);
        pr.setSpreadTasa(new BigDecimal("0.10"));
        pr.setPrecioReferencia(45_000);

        ParametroMacroEntity pm = new ParametroMacroEntity();
        pm.setId(1L);
        pm.setIpsPatronal(new BigDecimal("0.165"));
        pm.setAguinaldoFactor(BigDecimal.ZERO);
        pm.setTasaIre(new BigDecimal("0.10"));
        pm.setSalarioMinimoQ1(800_000);

        ParametroMacroTrimestreEntity mt = new ParametroMacroTrimestreEntity();
        mt.setInflacionTrim(BigDecimal.ZERO);           // inflación acumulada = 1.0
        mt.setTipoCambio(new BigDecimal("6700"));
        mt.setTpmAnual(new BigDecimal("0.20"));

        SnapshotEstadoEntity snap = new SnapshotEstadoEntity();
        snap.setCaja(20_000_000);
        snap.setCapacidad(1_000);
        snap.setDeuda(1_000_000);
        snap.setInventario(100);
        snap.setHeadcount((short) 5);
        snap.setSalario(1_000_000);
        snap.setIdAcumulado(0);
        snap.setValorPlanta(10_000_000);

        when(trimestreRepo.findById(any())).thenReturn(Optional.of(tri));
        when(competenciaRepo.findById(any())).thenReturn(Optional.of(comp));
        when(parametroRubroRepo.findByRubroIdActivos(anyLong())).thenReturn(List.of(pr));
        when(parametroMacroRepo.findById(any())).thenReturn(Optional.of(pm));
        when(macroTrimestreRepo.findByMacroId(any())).thenReturn(List.of(mt));
        when(snapshotRepo.findByEquipoIdAndTrimestreIdAndMomento(any(), any(), any()))
                .thenReturn(Optional.of(snap));
        when(eventoCompRepo.findActivosParaTrimestre(any(), any())).thenReturn(List.of());
    }

    private DecisionInput input() {
        DecisionInput in = new DecisionInput();
        in.setPrecioVenta(50_000L);
        in.setProduccionPlanificada(800L);
        in.setInversionMarketing(1_000_000L);
        in.setInversionId(500_000L);
        in.setInversionCapacitacion(0L);
        in.setInversionCapacidad(0L);
        in.setPrestamoSolicitado(2_000_000L);
        in.setDividendosPagar(0L);
        in.setContratacionesNetas(0);
        in.setAumentoSalarialPct(0f);
        return in;
    }

    @Test
    void proyeccion_espeja_terminos_del_motor() {
        stubFixtures();

        ProyeccionFinancieraDTO p = service().calcularProyeccion(EQUIPO_ID, TRIMESTRE_ID, input());

        // Ingresos = ventas(=producción 800) × precio 50.000
        assertThat(p.ingresosEstimados()).isEqualTo(40_000_000L);
        // MP = 800 × 5.000 (costo unitario base, params neutros)
        assertThat(p.costosVariablesEst()).isEqualTo(4_000_000L);
        // Costo fijo = base × inflación acumulada (1.0)
        assertThat(p.costosFijosEst()).isEqualTo(3_000_000L);
        // Costo laboral = salario 1.000.000 × hc 5 × 3 meses × (1 + IPS 0,165) = 17.475.000
        // (con el bug anterior, sin ×3, daba 5.825.000)
        assertThat(p.costoLaboralEst()).isEqualTo(17_475_000L);
        // Intereses = deuda inicial 1.000.000 × (0,20+0,10)/4 = 75.000
        // (con el bug anterior, sobre deuda+préstamo 3.000.000, daba 225.000)
        assertThat(p.interesesEst()).isEqualTo(75_000L);
        // Inversión total mostrada = capacidad + marketing + I+D + capacitación
        assertThat(p.inversionTotal()).isEqualTo(1_500_000L);
        assertThat(p.utilizacionPlanta()).isEqualTo(0.8);

        // Caja proyectada incluye IRE (1.344.000) y almacenamiento (10.000):
        // 20.000.000 + utilidadNeta 12.096.000 + deprec 500.000 + préstamo 2.000.000 = 34.596.000
        assertThat(p.cajaProyectada()).isEqualTo(34_596_000L);
        assertThat(p.semaforoCaja()).isEqualTo("verde");
    }
}

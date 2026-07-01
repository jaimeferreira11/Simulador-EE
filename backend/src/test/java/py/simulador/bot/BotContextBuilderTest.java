package py.simulador.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import py.simulador.catalogo.EventoCatalogoEntity;
import py.simulador.catalogo.EventoCatalogoRepository;
import py.simulador.catalogo.ParametroMacroEntity;
import py.simulador.catalogo.ParametroMacroRepository;
import py.simulador.catalogo.ParametroMacroTrimestreEntity;
import py.simulador.catalogo.ParametroMacroTrimestreRepository;
import py.simulador.catalogo.ParametroRubroEntity;
import py.simulador.catalogo.ParametroRubroRepository;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.equipo.EquipoEntity;
import py.simulador.equipo.EquipoRepository;
import py.simulador.evento.EventoCompetenciaEntity;
import py.simulador.evento.EventoCompetenciaRepository;
import py.simulador.resultado.RankingTrimestreEntity;
import py.simulador.resultado.RankingTrimestreRepository;
import py.simulador.resultado.ResultadoCalculoEntity;
import py.simulador.resultado.ResultadoCalculoRepository;
import py.simulador.resultado.SnapshotEstadoEntity;
import py.simulador.resultado.SnapshotEstadoRepository;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class BotContextBuilderTest {

    private static final long COMPETENCIA_ID = 7L;
    private static final long TRIMESTRE_ID = 42L;
    private static final long BOT_ID = 101L;
    private static final long RIVAL_ID = 102L;
    private static final long MACRO_ID = 11L;
    private static final long RUBRO_PARAM_ID = 22L;

    @Mock SnapshotEstadoRepository snapshotRepo;
    @Mock CompetenciaRepository competenciaRepo;
    @Mock ParametroMacroRepository macroRepo;
    @Mock ParametroMacroTrimestreRepository macroTrimRepo;
    @Mock ParametroRubroRepository rubroParamRepo;
    @Mock EventoCompetenciaRepository eventoCompRepo;
    @Mock EventoCatalogoRepository eventoCatalogoRepo;
    @Mock TrimestreRepository trimestreRepo;
    @Mock ResultadoCalculoRepository resultadoRepo;
    @Mock RankingTrimestreRepository rankingRepo;
    @Mock EquipoRepository equipoRepo;

    @InjectMocks BotContextBuilder builder;

    private EquipoEntity bot;
    private EquipoEntity rival;
    private TrimestreEntity q1;
    private TrimestreEntity q2;
    private CompetenciaEntity competencia;

    @BeforeEach
    void setup() {
        bot = new EquipoEntity();
        bot.setId(BOT_ID);
        bot.setCompetenciaId(COMPETENCIA_ID);
        bot.setNombreEmpresa("Bot Uno");
        bot.setTipo("BOT");
        bot.setDificultad("DIFICIL");
        bot.setPersonalidad("AGRESIVO");

        rival = new EquipoEntity();
        rival.setId(RIVAL_ID);
        rival.setCompetenciaId(COMPETENCIA_ID);
        rival.setNombreEmpresa("Humano Rival");
        rival.setTipo("HUMANO");

        q1 = new TrimestreEntity();
        q1.setId(40L);
        q1.setCompetenciaId(COMPETENCIA_ID);
        q1.setNumero((short) 1);
        q1.setEstado("PROCESADO");

        q2 = new TrimestreEntity();
        q2.setId(TRIMESTRE_ID);
        q2.setCompetenciaId(COMPETENCIA_ID);
        q2.setNumero((short) 2);
        q2.setEstado("ABIERTO_DECISIONES");

        competencia = new CompetenciaEntity();
        competencia.setId(COMPETENCIA_ID);
        competencia.setParametroMacroId(MACRO_ID);
        competencia.setParametroRubroId(RUBRO_PARAM_ID);
    }

    @Test
    void q1_emptyEnvironment_returnsConservativeDefaults() {
        // No snapshot, no competencia, no events, no rankings — heuristic falls back.
        lenient().when(snapshotRepo.findByEquipoIdAndTrimestreIdAndMomento(BOT_ID, q1.getId(), "INICIO"))
                .thenReturn(Optional.empty());
        lenient().when(competenciaRepo.findById(COMPETENCIA_ID)).thenReturn(Optional.empty());
        lenient().when(eventoCompRepo.findActivosParaTrimestre(COMPETENCIA_ID, q1.getId()))
                .thenReturn(List.of());
        lenient().when(equipoRepo.findByCompetenciaId(COMPETENCIA_ID)).thenReturn(List.of(bot));

        BotContext ctx = builder.build(bot, q1);

        assertThat(ctx.equipoId()).isEqualTo(BOT_ID);
        assertThat(ctx.trimestreId()).isEqualTo(q1.getId());
        assertThat(ctx.cajaActual()).isZero();
        assertThat(ctx.costoUnitarioEstimado()).isEqualTo(32_000L); // fallback
        assertThat(ctx.parametrosMacro()).isEmpty();
        assertThat(ctx.eventosActivos()).isEmpty();
        assertThat(ctx.competidores()).isEmpty();
        assertThat(ctx.esPrimerTrimestre()).isTrue();
        assertThat(ctx.posicionRankingAnterior()).isNull();
        assertThat(ctx.totalEquipos()).isNull();
        // Sin snapshot, capacidad cae al sentinel (clamp deshabilitado).
        assertThat(ctx.capacidadActual()).isEqualTo(BotContext.CAPACIDAD_DESCONOCIDA);
    }

    @Test
    void q2_withActiveEventAndPreviousRanking_wiresEverything() {
        // ---- Snapshot for the bot at INICIO Q2 ----
        SnapshotEstadoEntity snap = new SnapshotEstadoEntity();
        snap.setCaja(120_000_000L);
        snap.setInventario(5_000L);
        snap.setIdAcumulado(3_500_000L);
        snap.setBrandEquity(new BigDecimal("0.72"));
        snap.setCapacidad(45_000L);
        lenient().when(snapshotRepo.findByEquipoIdAndTrimestreIdAndMomento(BOT_ID, TRIMESTRE_ID, "INICIO"))
                .thenReturn(Optional.of(snap));

        // ---- Competencia + rubro param (costoUnitMp drives costoUnitarioEstimado) ----
        ParametroRubroEntity pr = new ParametroRubroEntity();
        pr.setId(RUBRO_PARAM_ID);
        pr.setCostoUnitMp(48_000L);
        lenient().when(competenciaRepo.findById(COMPETENCIA_ID)).thenReturn(Optional.of(competencia));
        lenient().when(rubroParamRepo.findById(RUBRO_PARAM_ID)).thenReturn(Optional.of(pr));

        // ---- Macro params (set-level + per-Q row) ----
        ParametroMacroEntity macro = new ParametroMacroEntity();
        macro.setId(MACRO_ID);
        macro.setSalarioMinimoQ1(2_798_309L);
        macro.setSalarioMinimoQ4(2_900_000L);
        macro.setIpsPatronal(new BigDecimal("0.165"));
        macro.setIpsTrabajador(new BigDecimal("0.090"));
        macro.setTasaIre(new BigDecimal("0.10"));
        macro.setIvaGeneral(new BigDecimal("0.10"));
        lenient().when(macroRepo.findById(MACRO_ID)).thenReturn(Optional.of(macro));

        ParametroMacroTrimestreEntity mt2 = new ParametroMacroTrimestreEntity();
        mt2.setMacroId(MACRO_ID);
        mt2.setTrimestre(2);
        mt2.setInflacionTrim(new BigDecimal("0.012"));
        mt2.setTipoCambio(new BigDecimal("7350.00"));
        mt2.setTpmAnual(new BigDecimal("0.0625"));
        lenient().when(macroTrimRepo.findByMacroId(MACRO_ID)).thenReturn(List.of(mt2));

        // ---- Active event affecting costs (diesel) ----
        EventoCompetenciaEntity ec = new EventoCompetenciaEntity();
        ec.setId(900L);
        ec.setCompetenciaId(COMPETENCIA_ID);
        ec.setTrimestreId(TRIMESTRE_ID);
        ec.setEventoCatalogoId(500L);
        ec.setMagnitudAplicada(new BigDecimal("0.08"));
        ec.setDuracionAplicada((short) 1);
        lenient().when(eventoCompRepo.findActivosParaTrimestre(COMPETENCIA_ID, TRIMESTRE_ID))
                .thenReturn(List.of(ec));

        EventoCatalogoEntity cat = new EventoCatalogoEntity();
        cat.setId(500L);
        cat.setCodigo("DIESEL_ALZA");
        cat.setDescripcion("Suba de combustible");
        cat.setSeveridad("ALTA");
        cat.setTipoEfecto("COSTO_LOGISTICO");
        cat.setMagnitudDefault(new BigDecimal("0.08"));
        lenient().when(eventoCatalogoRepo.findById(500L)).thenReturn(Optional.of(cat));

        // ---- Previous trimestre + result + ranking ----
        lenient().when(trimestreRepo.findByCompetenciaIdAndNumero(COMPETENCIA_ID, (short) 1))
                .thenReturn(Optional.of(q1));

        ResultadoCalculoEntity rc = new ResultadoCalculoEntity();
        rc.setEquipoId(BOT_ID);
        rc.setTrimestreId(q1.getId());
        rc.setIngresos(85_000_000L);
        rc.setUtilidadNeta(12_500_000L);
        lenient().when(resultadoRepo.findByEquipoIdAndTrimestreId(BOT_ID, q1.getId()))
                .thenReturn(Optional.of(rc));

        RankingTrimestreEntity rBot = new RankingTrimestreEntity();
        rBot.setEquipoId(BOT_ID);
        rBot.setPosicion((short) 2);
        RankingTrimestreEntity rRival = new RankingTrimestreEntity();
        rRival.setEquipoId(RIVAL_ID);
        rRival.setPosicion((short) 1);
        lenient().when(rankingRepo.findByCompetenciaIdAndTrimestreId(COMPETENCIA_ID, q1.getId()))
                .thenReturn(List.of(rRival, rBot));

        // ---- Competitors (rival + self; self must be filtered out) ----
        lenient().when(equipoRepo.findByCompetenciaId(COMPETENCIA_ID))
                .thenReturn(List.of(bot, rival));

        // ----------------- exercise -----------------
        BotContext ctx = builder.build(bot, q2);

        // Own state from snapshot
        assertThat(ctx.cajaActual()).isEqualTo(120_000_000L);
        assertThat(ctx.inventarioUnidades()).isEqualTo(5_000L);
        assertThat(ctx.rdAcumulado()).isEqualTo(3_500_000L);
        assertThat(ctx.brandEquity()).isEqualTo(0.72);
        // Capacidad propagada desde el snapshot — habilita el clamp del bot.
        assertThat(ctx.capacidadActual()).isEqualTo(45_000L);

        // Costo unitario from rubro param
        assertThat(ctx.costoUnitarioEstimado()).isEqualTo(48_000L);

        // Macros — both set-level and per-Q
        assertThat(ctx.parametrosMacro())
                .containsEntry("salario_minimo_q1", 2_798_309L)
                .containsEntry("ips_patronal", new BigDecimal("0.165"))
                .containsEntry("inflacion_trim", new BigDecimal("0.012"))
                .containsEntry("tipo_cambio_usd", new BigDecimal("7350.00"))
                .containsEntry("tpm_anual", new BigDecimal("0.0625"));

        // Eventos: one active event, with the heuristic-friendly key wired
        assertThat(ctx.eventosActivos()).hasSize(1);
        BotContext.EventoActivo eAct = ctx.eventosActivos().get(0);
        assertThat(eAct.codigo()).isEqualTo("DIESEL_ALZA");
        assertThat(eAct.efectos())
                .containsKey("magnitud")
                .containsKey("costo_unitario_delta") // key heuristic reads
                .containsKey("costo_logistico");     // generic tipo_efecto key
        assertThat(eAct.efectos().get("costo_unitario_delta"))
                .isEqualTo(new BigDecimal("0.08"));

        // Previous results
        assertThat(ctx.esPrimerTrimestre()).isFalse();
        assertThat(ctx.ingresoTrimestreAnterior()).isEqualTo(85_000_000L);
        assertThat(ctx.gananciaTrimestreAnterior()).isEqualTo(12_500_000L);
        assertThat(ctx.posicionRankingAnterior()).isEqualTo(2);
        assertThat(ctx.totalEquipos()).isEqualTo(2);

        // Competitors: rival only (self excluded), tipo + posicion populated
        assertThat(ctx.competidores()).hasSize(1);
        BotContext.Competidor c = ctx.competidores().get(0);
        assertThat(c.equipoId()).isEqualTo(RIVAL_ID);
        assertThat(c.nombre()).isEqualTo("Humano Rival");
        assertThat(c.tipo()).isEqualTo("HUMANO");
        assertThat(c.posicionAnterior()).isEqualTo(1);
    }
}

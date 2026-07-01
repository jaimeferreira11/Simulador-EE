package py.simulador.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import py.simulador.bot.log.BotDecisionLogRepository;
import py.simulador.bot.model.Difficulty;
import py.simulador.bot.model.Personality;
import py.simulador.common.BusinessValidationException;
import py.simulador.common.InvalidStateTransitionException;
import py.simulador.common.ResourceNotFoundException;
import py.simulador.decision.DecisionEquipoEntity;
import py.simulador.decision.DecisionService;
import py.simulador.equipo.EquipoEntity;
import py.simulador.equipo.EquipoRepository;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;
import py.simulador.usuario.UsuarioEntity;
import py.simulador.usuario.UsuarioRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BotDecisionServiceTest {

    private static final long SYSTEM_BOT_USER_ID = 999L;
    private static final long TRIMESTRE_ID = 42L;
    private static final long COMPETENCIA_ID = 7L;

    @Mock EquipoRepository equipoRepo;
    @Mock TrimestreRepository trimestreRepo;
    @Mock BotStrategyFactory factory;
    @Mock BotContextBuilder contextBuilder;
    @Mock DecisionService decisionService;
    @Mock UsuarioRepository usuarioRepo;
    @Mock BotDecisionLogRepository logRepo;
    @Mock BotStrategy strategy;

    @InjectMocks
    BotDecisionService service;

    @BeforeEach
    void initSystemBot() {
        UsuarioEntity sysBot = new UsuarioEntity();
        sysBot.setId(SYSTEM_BOT_USER_ID);
        when(usuarioRepo.findByEmail("system-bot@simulador.local"))
                .thenReturn(Optional.of(sysBot));
        // Replicate the @PostConstruct init() that Spring would normally invoke.
        service.init();
    }

    private TrimestreEntity trimestreAbierto() {
        TrimestreEntity t = new TrimestreEntity();
        t.setId(TRIMESTRE_ID);
        t.setCompetenciaId(COMPETENCIA_ID);
        t.setEstado("ABIERTO_DECISIONES");
        t.setNumero((short) 1);
        return t;
    }

    private EquipoEntity bot(long id, String nombre) {
        EquipoEntity e = new EquipoEntity();
        e.setId(id);
        e.setCompetenciaId(COMPETENCIA_ID);
        e.setTipo("BOT");
        e.setNombreEmpresa(nombre);
        e.setDificultad("MEDIO");
        e.setPersonalidad("BALANCEADO");
        return e;
    }

    private BotContext minimalContext(long equipoId) {
        return new BotContext(
                equipoId, TRIMESTRE_ID,
                Difficulty.MEDIO, Personality.BALANCEADO,
                10_000_000L, 0L, 0.5, 0L, 32_000L,
                BotContext.CAPACIDAD_DESCONOCIDA,
                null, null, null, null,
                Map.of(), List.of(), List.of()
        );
    }

    private BotDecisionDTO sampleDecision() {
        return new BotDecisionDTO(
                40_000L, 1000L, 2_000_000L, 1_000_000L,
                3, 2_700_000L, 0L, 0L, "test"
        );
    }

    @Test
    void happyPath_callsStrategyAndUpsertForEachBot() {
        TrimestreEntity tri = trimestreAbierto();
        EquipoEntity b1 = bot(101L, "Bot Uno");
        EquipoEntity b2 = bot(102L, "Bot Dos");

        when(trimestreRepo.findById(TRIMESTRE_ID)).thenReturn(Optional.of(tri));
        when(equipoRepo.findByCompetenciaIdAndTipo(COMPETENCIA_ID, "BOT"))
                .thenReturn(List.of(b1, b2));

        BotContext ctx1 = minimalContext(b1.getId());
        BotContext ctx2 = minimalContext(b2.getId());
        when(contextBuilder.build(b1, tri)).thenReturn(ctx1);
        when(contextBuilder.build(b2, tri)).thenReturn(ctx2);

        when(factory.forDifficulty(Difficulty.MEDIO, Personality.BALANCEADO))
                .thenReturn(strategy);

        BotDecisionDTO dec = sampleDecision();
        when(strategy.generate(any(BotContext.class))).thenReturn(dec);

        service.generarDecisionesParaTrimestreAbierto(TRIMESTRE_ID);

        verify(contextBuilder).build(b1, tri);
        verify(contextBuilder).build(b2, tri);
        verify(strategy, times(2)).generate(any(BotContext.class));
        verify(decisionService).upsertDecisionBot(
                eq(b1.getId()), eq(TRIMESTRE_ID), eq(dec), eq(SYSTEM_BOT_USER_ID));
        verify(decisionService).upsertDecisionBot(
                eq(b2.getId()), eq(TRIMESTRE_ID), eq(dec), eq(SYSTEM_BOT_USER_ID));
    }

    @Test
    void perBotErrorIsolation_oneStrategyFails_othersStillGetDecisions() {
        TrimestreEntity tri = trimestreAbierto();
        EquipoEntity b1 = bot(101L, "Bot Uno");
        EquipoEntity b2 = bot(102L, "Bot Dos (falla)");
        EquipoEntity b3 = bot(103L, "Bot Tres");

        when(trimestreRepo.findById(TRIMESTRE_ID)).thenReturn(Optional.of(tri));
        when(equipoRepo.findByCompetenciaIdAndTipo(COMPETENCIA_ID, "BOT"))
                .thenReturn(List.of(b1, b2, b3));

        when(contextBuilder.build(b1, tri)).thenReturn(minimalContext(b1.getId()));
        when(contextBuilder.build(b2, tri))
                .thenThrow(new RuntimeException("ctx-builder boom"));
        when(contextBuilder.build(b3, tri)).thenReturn(minimalContext(b3.getId()));

        when(factory.forDifficulty(Difficulty.MEDIO, Personality.BALANCEADO))
                .thenReturn(strategy);

        BotDecisionDTO dec = sampleDecision();
        when(strategy.generate(any(BotContext.class))).thenReturn(dec);

        // Should not throw — error in b2 is swallowed
        service.generarDecisionesParaTrimestreAbierto(TRIMESTRE_ID);

        // b1 and b3 should still get decisions; b2 must not.
        verify(decisionService).upsertDecisionBot(
                eq(b1.getId()), eq(TRIMESTRE_ID), any(), eq(SYSTEM_BOT_USER_ID));
        verify(decisionService).upsertDecisionBot(
                eq(b3.getId()), eq(TRIMESTRE_ID), any(), eq(SYSTEM_BOT_USER_ID));
        verify(decisionService, never()).upsertDecisionBot(
                eq(b2.getId()), anyLong(), any(), anyLong());
    }

    @Test
    void emptyBotsList_noStrategyOrUpsertCalls() {
        TrimestreEntity tri = trimestreAbierto();

        when(trimestreRepo.findById(TRIMESTRE_ID)).thenReturn(Optional.of(tri));
        when(equipoRepo.findByCompetenciaIdAndTipo(COMPETENCIA_ID, "BOT"))
                .thenReturn(List.of());

        // Must not throw and must not invoke strategy/decisionService
        service.generarDecisionesParaTrimestreAbierto(TRIMESTRE_ID);

        verify(contextBuilder, never()).build(any(), any());
        verify(strategy, never()).generate(any());
        verify(decisionService, never())
                .upsertDecisionBot(anyLong(), anyLong(), any(), anyLong());
    }

    // ========================================================================
    // regenerarDecisionDeBot — endpoint moderador para re-tirar decision bot
    // ========================================================================

    @Test
    void regenerar_happyPath_callsStrategyAndUpsertOnce() {
        TrimestreEntity tri = trimestreAbierto();
        EquipoEntity b1 = bot(101L, "Bot Uno");

        when(equipoRepo.findById(b1.getId())).thenReturn(Optional.of(b1));
        when(trimestreRepo.findById(TRIMESTRE_ID)).thenReturn(Optional.of(tri));

        BotContext ctx = minimalContext(b1.getId());
        when(contextBuilder.build(b1, tri)).thenReturn(ctx);
        when(factory.forDifficulty(Difficulty.MEDIO, Personality.BALANCEADO))
                .thenReturn(strategy);

        BotDecisionDTO dec = sampleDecision();
        when(strategy.generate(any(BotContext.class))).thenReturn(dec);

        DecisionEquipoEntity persisted = new DecisionEquipoEntity();
        persisted.setEquipoId(b1.getId());
        persisted.setTrimestreId(TRIMESTRE_ID);
        persisted.setEstado("ENVIADA");
        when(decisionService.upsertDecisionBot(
                eq(b1.getId()), eq(TRIMESTRE_ID), eq(dec), eq(SYSTEM_BOT_USER_ID)))
                .thenReturn(persisted);

        DecisionEquipoEntity result = service.regenerarDecisionDeBot(b1.getId(), TRIMESTRE_ID, 555L);

        assertThat(result).isSameAs(persisted);
        verify(contextBuilder, times(1)).build(b1, tri);
        verify(strategy, times(1)).generate(any(BotContext.class));
        verify(decisionService, times(1)).upsertDecisionBot(
                eq(b1.getId()), eq(TRIMESTRE_ID), eq(dec), eq(SYSTEM_BOT_USER_ID));
    }

    @Test
    void regenerar_rejectsNonBotEquipo_with422() {
        EquipoEntity humano = new EquipoEntity();
        humano.setId(101L);
        humano.setCompetenciaId(COMPETENCIA_ID);
        humano.setTipo("HUMANO");
        humano.setNombreEmpresa("Equipo Humano");

        when(equipoRepo.findById(humano.getId())).thenReturn(Optional.of(humano));

        assertThatThrownBy(() ->
                service.regenerarDecisionDeBot(humano.getId(), TRIMESTRE_ID, 555L))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("Solo equipos bot pueden regenerar decisiones");

        verify(contextBuilder, never()).build(any(), any());
        verify(strategy, never()).generate(any());
        verify(decisionService, never())
                .upsertDecisionBot(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    void regenerar_rejectsTrimestreNoAbierto_with409() {
        EquipoEntity b1 = bot(101L, "Bot Uno");
        TrimestreEntity tri = trimestreAbierto();
        tri.setEstado("PROCESADO");

        when(equipoRepo.findById(b1.getId())).thenReturn(Optional.of(b1));
        when(trimestreRepo.findById(TRIMESTRE_ID)).thenReturn(Optional.of(tri));

        assertThatThrownBy(() ->
                service.regenerarDecisionDeBot(b1.getId(), TRIMESTRE_ID, 555L))
                .isInstanceOf(InvalidStateTransitionException.class);

        verify(contextBuilder, never()).build(any(), any());
        verify(strategy, never()).generate(any());
        verify(decisionService, never())
                .upsertDecisionBot(anyLong(), anyLong(), any(), anyLong());
    }
}

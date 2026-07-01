package py.simulador.decision;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import py.simulador.auditoria.AuditoriaService;
import py.simulador.bot.BotDecisionDTO;
import py.simulador.catalogo.AreaDecisionRepository;
import py.simulador.common.BusinessValidationException;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.equipo.EquipoMiembroRepository;
import py.simulador.resultado.SnapshotEstadoRepository;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DecisionService#upsertDecisionBot} that focus on the
 * gross-error precondition check applied to {@link BotDecisionDTO} BEFORE
 * persistence.
 *
 * <p>This guards the persistence boundary against buggy strategies (current
 * or future, e.g. LlmBotStrategy) that might emit invalid values which would
 * otherwise corrupt the engine.
 */
@ExtendWith(MockitoExtension.class)
class DecisionServiceBotTest {

    private static final long EQUIPO_ID = 101L;
    private static final long TRIMESTRE_ID = 42L;
    private static final long SYSTEM_USER_ID = 999L;

    @Mock DecisionEquipoRepository decisionRepo;
    @Mock DecisionCampoLogRepository campoLogRepo;
    @Mock AreaDecisionRepository areaRepo;
    @Mock TrimestreRepository trimestreRepo;
    @Mock CompetenciaRepository competenciaRepo;
    @Mock EquipoMiembroRepository miembroRepo;
    @Mock SnapshotEstadoRepository snapshotRepo;
    @Mock AuditoriaService auditoria;
    @Mock ContextoDecisionService contextoDecisionService;

    private DecisionService decisionService;

    @BeforeEach
    void setUp() {
        decisionService = new DecisionService(
                decisionRepo, campoLogRepo, areaRepo, trimestreRepo,
                competenciaRepo, miembroRepo, snapshotRepo, auditoria,
                contextoDecisionService);
    }

    private TrimestreEntity trimestreAbierto() {
        TrimestreEntity t = new TrimestreEntity();
        t.setId(TRIMESTRE_ID);
        t.setEstado("ABIERTO_DECISIONES");
        t.setNumero((short) 1);
        return t;
    }

    private BotDecisionDTO validDecision() {
        return new BotDecisionDTO(
                40_000L, 1000L, 2_000_000L, 1_000_000L,
                3, 2_700_000L, 0L, 0L, "ok"
        );
    }

    // ------------------------------------------------------------------
    // Invalid DTOs are rejected BEFORE any persistence call.
    // ------------------------------------------------------------------

    @Test
    void upsertDecisionBot_rejects_negative_precio() {
        var invalid = new BotDecisionDTO(
                -1L, 100L, 0L, 0L, 2, 2_700_000L, 0L, 0L, "test"
        );

        assertThatThrownBy(() -> decisionService.upsertDecisionBot(
                EQUIPO_ID, TRIMESTRE_ID, invalid, SYSTEM_USER_ID))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("precio");

        verify(decisionRepo, never()).save(any());
    }

    @Test
    void upsertDecisionBot_rejects_zero_precio() {
        var invalid = new BotDecisionDTO(
                0L, 100L, 0L, 0L, 2, 2_700_000L, 0L, 0L, "test"
        );

        assertThatThrownBy(() -> decisionService.upsertDecisionBot(
                EQUIPO_ID, TRIMESTRE_ID, invalid, SYSTEM_USER_ID))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("precio");

        verify(decisionRepo, never()).save(any());
    }

    @Test
    void upsertDecisionBot_rejects_negative_produccion() {
        var invalid = new BotDecisionDTO(
                10_000L, -5L, 0L, 0L, 2, 2_700_000L, 0L, 0L, "test"
        );

        assertThatThrownBy(() -> decisionService.upsertDecisionBot(
                EQUIPO_ID, TRIMESTRE_ID, invalid, SYSTEM_USER_ID))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("produccion");

        verify(decisionRepo, never()).save(any());
    }

    @Test
    void upsertDecisionBot_rejects_negative_marketing() {
        var invalid = new BotDecisionDTO(
                10_000L, 100L, -1L, 0L, 2, 2_700_000L, 0L, 0L, "test"
        );

        assertThatThrownBy(() -> decisionService.upsertDecisionBot(
                EQUIPO_ID, TRIMESTRE_ID, invalid, SYSTEM_USER_ID))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("marketing");

        verify(decisionRepo, never()).save(any());
    }

    @Test
    void upsertDecisionBot_rejects_negative_rd() {
        var invalid = new BotDecisionDTO(
                10_000L, 100L, 0L, -1L, 2, 2_700_000L, 0L, 0L, "test"
        );

        assertThatThrownBy(() -> decisionService.upsertDecisionBot(
                EQUIPO_ID, TRIMESTRE_ID, invalid, SYSTEM_USER_ID))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("I+D");

        verify(decisionRepo, never()).save(any());
    }

    @Test
    void upsertDecisionBot_rejects_zero_empleados() {
        var invalid = new BotDecisionDTO(
                10_000L, 100L, 0L, 0L, 0, 2_700_000L, 0L, 0L, "test"
        );

        assertThatThrownBy(() -> decisionService.upsertDecisionBot(
                EQUIPO_ID, TRIMESTRE_ID, invalid, SYSTEM_USER_ID))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("empleados");

        verify(decisionRepo, never()).save(any());
    }

    @Test
    void upsertDecisionBot_rejects_negative_empleados() {
        var invalid = new BotDecisionDTO(
                10_000L, 100L, 0L, 0L, -1, 2_700_000L, 0L, 0L, "test"
        );

        assertThatThrownBy(() -> decisionService.upsertDecisionBot(
                EQUIPO_ID, TRIMESTRE_ID, invalid, SYSTEM_USER_ID))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("empleados");

        verify(decisionRepo, never()).save(any());
    }

    @Test
    void upsertDecisionBot_rejects_negative_salario() {
        var invalid = new BotDecisionDTO(
                10_000L, 100L, 0L, 0L, 2, -1L, 0L, 0L, "test"
        );

        assertThatThrownBy(() -> decisionService.upsertDecisionBot(
                EQUIPO_ID, TRIMESTRE_ID, invalid, SYSTEM_USER_ID))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("salario");

        verify(decisionRepo, never()).save(any());
    }

    @Test
    void upsertDecisionBot_rejects_negative_prestamo() {
        var invalid = new BotDecisionDTO(
                10_000L, 100L, 0L, 0L, 2, 2_700_000L, -1L, 0L, "test"
        );

        assertThatThrownBy(() -> decisionService.upsertDecisionBot(
                EQUIPO_ID, TRIMESTRE_ID, invalid, SYSTEM_USER_ID))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("prestamo");

        verify(decisionRepo, never()).save(any());
    }

    @Test
    void upsertDecisionBot_rejects_negative_inversion_financiera() {
        var invalid = new BotDecisionDTO(
                10_000L, 100L, 0L, 0L, 2, 2_700_000L, 0L, -1L, "test"
        );

        assertThatThrownBy(() -> decisionService.upsertDecisionBot(
                EQUIPO_ID, TRIMESTRE_ID, invalid, SYSTEM_USER_ID))
                .isInstanceOf(BusinessValidationException.class)
                .hasMessageContaining("financiera");

        verify(decisionRepo, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Sanity: a valid DTO does NOT trigger the validation exception
    // (it proceeds to the persistence path, which we don't fully verify
    // here — the existing happy-path tests cover that).
    // ------------------------------------------------------------------

    @Test
    void upsertDecisionBot_accepts_valid_decision() {
        when(trimestreRepo.findById(TRIMESTRE_ID)).thenReturn(Optional.of(trimestreAbierto()));
        when(decisionRepo.findByEquipoIdAndTrimestreId(EQUIPO_ID, TRIMESTRE_ID))
                .thenReturn(Optional.empty());
        when(decisionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> decisionService.upsertDecisionBot(
                EQUIPO_ID, TRIMESTRE_ID, validDecision(), SYSTEM_USER_ID))
                .doesNotThrowAnyException();

        verify(decisionRepo).save(any());
    }
}

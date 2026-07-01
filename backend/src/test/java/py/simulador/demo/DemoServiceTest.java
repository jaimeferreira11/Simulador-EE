package py.simulador.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import py.simulador.IntegrationTestBase;
import py.simulador.common.BusinessValidationException;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.decision.DecisionEquipoEntity;
import py.simulador.equipo.EquipoEntity;
import py.simulador.equipo.EquipoRepository;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;
import py.simulador.trimestre.TrimestreService;
import py.simulador.usuario.UsuarioEntity;
import py.simulador.usuario.UsuarioRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static py.simulador.trimestre.TrimestreStateMachine.ABIERTO_DECISIONES;
import static py.simulador.trimestre.TrimestreStateMachine.PROCESADO;

/**
 * Integration tests for {@link DemoService#decisionCeo}.
 *
 * <p>Uses real PostgreSQL (Testcontainers via {@link IntegrationTestBase}) and
 * the DEMO seed applied by Flyway migration V202605261200.
 */
class DemoServiceTest extends IntegrationTestBase {

    @Autowired private DemoService demoService;
    @Autowired private CompetenciaRepository competenciaRepo;
    @Autowired private EquipoRepository equipoRepo;
    @Autowired private TrimestreRepository trimestreRepo;
    @Autowired private TrimestreService trimestreService;
    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private JdbcTemplate jdbcTemplate;

    private CompetenciaEntity demoComp;
    private EquipoEntity equipoHumano;
    private UsuarioEntity ceoUser;

    /** Minimal valid payload: only precio_venta is required; rest default to 0. */
    private static Map<String, Object> minimalPayload() {
        return Map.of("precio_venta", 45_000L);
    }

    @BeforeEach
    void setUp() {
        demoComp = competenciaRepo.findByCodigo(DemoConstants.COMPETENCIA_CODIGO)
                .orElseThrow(() -> new AssertionError("DEMO competencia not seeded"));

        equipoHumano = equipoRepo.findByCompetenciaIdAndTipo(demoComp.getId(), "HUMANO")
                .stream().findFirst()
                .orElseThrow(() -> new AssertionError("No HUMANO team in DEMO"));

        ceoUser = usuarioRepo.findByEmail(DemoConstants.CEO_EMAIL)
                .orElseThrow(() -> new AssertionError("CEO user not seeded"));

        // Reinicia DEMO antes de cada test para garantizar estado conocido:
        // EN_CURSO, Q1 ABIERTO_DECISIONES con snapshots y decisiones bot listas.
        // Sin esto, un test que finaliza la competencia ensucia los siguientes.
        demoService.reiniciar(demoComp.getId());
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("decisionCeo persiste con usuario CEO fantoche y equipo HUMANO en estado ENVIADA")
    void decisionCeo_persisteConUsuarioFantoche() {
        DecisionEquipoEntity result = demoService.decisionCeo(demoComp.getId(), minimalPayload());

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull();
        assertThat(result.getRegistradoPorUsuarioId())
                .as("La decisión debe estar atribuida al CEO sintético")
                .isEqualTo(ceoUser.getId());
        assertThat(result.getEquipoId())
                .as("La decisión debe pertenecer al equipo HUMANO")
                .isEqualTo(equipoHumano.getId());
        assertThat(result.getEstado())
                .as("La decisión debe quedar en estado ENVIADA")
                .isEqualTo("ENVIADA");
        assertThat(result.getPrecioVenta())
                .as("El precio de venta debe haberse persistido")
                .isEqualTo(45_000L);
        assertThat(result.getSubmittedAt()).isNotNull();
    }

    // -----------------------------------------------------------------------
    // Guard: non-DEMO competencia must be rejected
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("decisionCeo lanza NotDemoCompetenciaException para competencia no-DEMO")
    void decisionCeo_rechazaSiNoEsDemo() {
        // Use the seeded RTL-2026A competition (which is never DEMO)
        CompetenciaEntity otherComp = competenciaRepo.findByCodigo("RTL-2026A")
                .orElseThrow(() -> new AssertionError("Seed competencia RTL-2026A not found"));

        assertThatThrownBy(() -> demoService.decisionCeo(otherComp.getId(), minimalPayload()))
                .isInstanceOf(NotDemoCompetenciaException.class)
                .hasMessageContaining("RTL-2026A");
    }

    // -----------------------------------------------------------------------
    // avanzar: happy path — closes Q1, opens Q2
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("avanzar cierra el trimestre actual y abre el siguiente")
    void avanzar_cierraYAbreSiguienteTrimestre() {
        // Q1 is already open via setUp(). Bots have decisions from the abrir hook.
        // Send the CEO decision so all teams have submitted.
        List<TrimestreEntity> trimestres = trimestreRepo.findByCompetenciaId(demoComp.getId());
        TrimestreEntity q1 = trimestres.stream()
                .filter(t -> t.getNumero() == 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Q1 not found"));

        demoService.decisionCeo(demoComp.getId(), minimalPayload());

        AvanzarResult result = demoService.avanzar(demoComp.getId());

        assertThat(result.getTrimestreAnteriorId())
                .as("El trimestre cerrado debe ser Q1")
                .isEqualTo(q1.getId());
        assertThat(result.getTrimestreActualId())
                .as("Debe haberse abierto un trimestre siguiente")
                .isNotNull();
        assertThat(result.getCompetenciaEstado())
                .as("La competencia sigue EN_CURSO")
                .isEqualTo("EN_CURSO");

        // Verify Q1 is PROCESADO
        TrimestreEntity q1Reloaded = trimestreRepo.findById(q1.getId())
                .orElseThrow(() -> new AssertionError("Q1 disappeared"));
        assertThat(q1Reloaded.getEstado())
                .as("Q1 debe quedar PROCESADO")
                .isEqualTo(PROCESADO);

        // Verify Q2 is ABIERTO_DECISIONES
        TrimestreEntity q2 = trimestreRepo.findById(result.getTrimestreActualId())
                .orElseThrow(() -> new AssertionError("Q2 not found"));
        assertThat(q2.getNumero())
                .as("El siguiente trimestre debe ser el número 2")
                .isEqualTo((short) 2);
        assertThat(q2.getEstado())
                .as("Q2 debe estar ABIERTO_DECISIONES")
                .isEqualTo(ABIERTO_DECISIONES);
    }

    // -----------------------------------------------------------------------
    // avanzar: last quarter finalizes the competition
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("avanzar en el último trimestre finaliza la competencia")
    void avanzar_ultimoTrimestre_finalizaCompetencia() {
        // Fast-forward through all 4 quarters. After each avanzar the next trimestre
        // is open and bots already have their decisions; only CEO needs to submit.
        enviarDecisionYAvanzar(demoComp.getId()); // closes Q1, opens Q2
        enviarDecisionYAvanzar(demoComp.getId()); // closes Q2, opens Q3
        enviarDecisionYAvanzar(demoComp.getId()); // closes Q3, opens Q4

        // Final quarter: no next trimestre should be opened
        demoService.decisionCeo(demoComp.getId(), minimalPayload());
        AvanzarResult result = demoService.avanzar(demoComp.getId());

        assertThat(result.getTrimestreActualId())
                .as("No debe haber siguiente trimestre después del último")
                .isNull();
        assertThat(result.getCompetenciaEstado())
                .as("La competencia debe haber quedado FINALIZADA")
                .isEqualTo("FINALIZADA");
    }

    // -----------------------------------------------------------------------
    // reiniciar: happy path — wipes runtime data and reopens Q1 with bots
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("reiniciar borra datos de runtime y deja Q1 ABIERTO_DECISIONES con decisiones de bots")
    void reiniciar_borraDatosYDejaQ1AbiertoConBotsListos() {
        // Arrange: send CEO decision and advance so Q1 has decisions + results in DB
        demoService.decisionCeo(demoComp.getId(), minimalPayload());
        demoService.avanzar(demoComp.getId()); // closes Q1 (PROCESADO), opens Q2

        // Verify there are decisions in DB pre-reset
        int decisionesPre = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sim.decision_equipo de "
                        + "JOIN sim.trimestre t ON t.id = de.trimestre_id "
                        + "WHERE t.competencia_id = ?",
                Integer.class, demoComp.getId());
        assertThat(decisionesPre).as("Debe haber decisiones antes del reinicio").isGreaterThan(0);

        // Act
        demoService.reiniciar(demoComp.getId());

        // Assert: competencia back in EN_CURSO
        CompetenciaEntity compReloaded = competenciaRepo.findById(demoComp.getId())
                .orElseThrow(() -> new AssertionError("Competencia not found after reiniciar"));
        assertThat(compReloaded.getEstado())
                .as("La competencia debe volver a EN_CURSO")
                .isEqualTo("EN_CURSO");

        // Assert: exactly 4 trimestres (fresh)
        List<TrimestreEntity> trimestres = trimestreRepo.findByCompetenciaId(demoComp.getId());
        assertThat(trimestres)
                .as("Deben existir exactamente 4 trimestres tras el reinicio")
                .hasSize(4);

        // Assert: Q1 is ABIERTO_DECISIONES
        TrimestreEntity q1Reloaded = trimestres.stream()
                .filter(t -> t.getNumero() == 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Q1 not found after reiniciar"));
        assertThat(q1Reloaded.getEstado())
                .as("Q1 debe estar ABIERTO_DECISIONES tras reiniciar")
                .isEqualTo(ABIERTO_DECISIONES);

        // Assert: exactly 4 equipos preserved
        List<EquipoEntity> equipos = equipoRepo.findByCompetenciaId(demoComp.getId());
        assertThat(equipos)
                .as("Los 4 equipos deben conservarse")
                .hasSize(4);

        // Assert: bots regenerated their decisions on Q1 abrir
        int decisionesPost = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sim.decision_equipo de "
                        + "JOIN sim.trimestre t ON t.id = de.trimestre_id "
                        + "WHERE t.competencia_id = ?",
                Integer.class, demoComp.getId());
        assertThat(decisionesPost)
                .as("Los bots deben haber regenerado sus decisiones en Q1 tras reiniciar")
                .isGreaterThan(0);
    }

    // -----------------------------------------------------------------------
    // reiniciar: guard — non-DEMO competencia must be rejected
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("reiniciar lanza NotDemoCompetenciaException para competencia no-DEMO")
    void reiniciar_rechazaSiNoEsDemo() {
        CompetenciaEntity otherComp = competenciaRepo.findByCodigo("RTL-2026A")
                .orElseThrow(() -> new AssertionError("Seed competencia RTL-2026A not found"));

        assertThatThrownBy(() -> demoService.reiniciar(otherComp.getId()))
                .isInstanceOf(NotDemoCompetenciaException.class)
                .hasMessageContaining("RTL-2026A");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Sends a minimal CEO decision and advances the current open trimestre.
     * Used to fast-forward through multiple quarters in a single test.
     */
    private void enviarDecisionYAvanzar(Long compId) {
        demoService.decisionCeo(compId, minimalPayload());
        demoService.avanzar(compId);
    }
}

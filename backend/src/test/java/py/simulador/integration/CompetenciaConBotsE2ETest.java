package py.simulador.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import py.simulador.IntegrationTestBase;
import py.simulador.api.generated.model.CompetenciaCreate;
import py.simulador.api.generated.model.EquipoCreate;
import py.simulador.bot.model.Difficulty;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaService;
import py.simulador.competencia.CompetenciaStateMachine;
import py.simulador.decision.DecisionEquipoEntity;
import py.simulador.decision.DecisionEquipoRepository;
import py.simulador.equipo.EquipoEntity;
import py.simulador.equipo.EquipoService;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;
import py.simulador.trimestre.TrimestreService;
import py.simulador.trimestre.TrimestreStateMachine;
import py.simulador.usuario.UsuarioEntity;
import py.simulador.usuario.UsuarioRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E integration test for a competition that mixes humano and BOT teams.
 *
 * <p>Validates the <b>new bot behaviour</b> introduced in the Equipos Bot feature: when a
 * moderador opens a trimestre, {@link py.simulador.bot.BotDecisionService} must be triggered
 * inside {@link TrimestreService#abrir(Long)} so every BOT team gets a decision auto-generated
 * and persisted with state {@code ENVIADA}, attributed to the {@code system-bot} system user.
 *
 * <p>Scope: trimestre-open bot generation only. The full 4Q lifecycle (close + motor) is
 * covered by other tests (Golden File suite). We intentionally do not close trimestres here —
 * this test focuses on proving the open-trimestre hook end-to-end against a real PostgreSQL.
 *
 * <p>This test requires a real PostgreSQL via Testcontainers and will only run when Docker is
 * available. Without Docker the test will be skipped/fail at container start, which is the
 * same constraint that applies to {@code AuthIntegrationTest} and {@code AdminControllerTest}.
 */
class CompetenciaConBotsE2ETest extends IntegrationTestBase {

    @Autowired private CompetenciaService competenciaService;
    @Autowired private EquipoService equipoService;
    @Autowired private TrimestreService trimestreService;
    @Autowired private TrimestreRepository trimestreRepo;
    @Autowired private DecisionEquipoRepository decisionRepo;
    @Autowired private UsuarioRepository usuarioRepo;

    // Seed parameter IDs from V202604211007__seed_catalogos.sql (RETAIL_CONV / PY_2026_BASE).
    // Looked up dynamically via repository in the test setup.

    @Test
    @DisplayName("Abrir Q1 con humanos + bots genera decisiones ENVIADA para cada bot")
    void competencia_con_humanos_y_bots_genera_decisiones_de_bots_al_abrir_trimestre() {
        // -------------------------------------------------------------------
        // 1. Crear competencia en BORRADOR (4 trimestres, rubro retail)
        // -------------------------------------------------------------------
        Long moderadorId = usuarioRepo.findByEmail("moderador@simulador.py")
                .orElseThrow().getId();

        CompetenciaCreate input = new CompetenciaCreate();
        input.setNombre("E2E Bots + Humanos");
        input.setEntidadId(seedEntidadId());
        input.setRubroId(seedRubroId());
        input.setParametroMacroId(seedMacroId());
        input.setParametroRubroId(seedRubroParamId());
        input.setNumTrimestres(4);
        input.setNumEquiposMax(8);
        input.setCajaInicial(500_000_000L);
        input.setCapacidadInicial(50_000L);
        input.setHeadcountInicial(100);
        input.setSalarioInicial(3_500_000L);
        input.setValorPlantaInicial(2_500_000_000L);

        CompetenciaEntity competencia = competenciaService.create(input, moderadorId);
        assertThat(competencia.getEstado()).isEqualTo(CompetenciaStateMachine.BORRADOR);

        // -------------------------------------------------------------------
        // 2. Abrir inscripción y crear 2 equipos humanos + 2 equipos BOT
        // -------------------------------------------------------------------
        competenciaService.abrirInscripcion(competencia.getId());

        EquipoEntity humano1 = crearHumano(competencia.getId(), "Humanos Alfa", "#006B3F");
        EquipoEntity humano2 = crearHumano(competencia.getId(), "Humanos Beta", "#D4213D");

        EquipoEntity bot1 = equipoService.crearEquipoBot(
                competencia.getId(), "Bot Facil", "#1E3A5F", Difficulty.FACIL);
        EquipoEntity bot2 = equipoService.crearEquipoBot(
                competencia.getId(), "Bot Dificil", "#F47920", Difficulty.DIFICIL);

        assertThat(bot1.esBot()).isTrue();
        assertThat(bot2.esBot()).isTrue();
        assertThat(humano1.esBot()).isFalse();
        assertThat(humano2.esBot()).isFalse();

        // -------------------------------------------------------------------
        // 3. Asignar al menos un miembro a cada equipo HUMANO (precondición de iniciar)
        //    Reusamos jugadores seed; en otra competencia están libres.
        // -------------------------------------------------------------------
        Long capitan1 = usuarioRepo.findByEmail("capitan1@simulador.py").orElseThrow().getId();
        Long capitan2 = usuarioRepo.findByEmail("capitan2@simulador.py").orElseThrow().getId();
        equipoService.addMiembro(humano1.getId(), capitan1, null, true);
        equipoService.addMiembro(humano2.getId(), capitan2, null, true);

        // -------------------------------------------------------------------
        // 4. Iniciar competencia (BORRADOR/ABIERTA_INSCRIPCION -> EN_CURSO)
        //    Esto crea los 4 trimestres en estado PENDIENTE y snapshots INICIO.
        // -------------------------------------------------------------------
        competenciaService.iniciar(competencia.getId());
        CompetenciaEntity enCurso = competenciaService.findById(competencia.getId());
        assertThat(enCurso.getEstado()).isEqualTo(CompetenciaStateMachine.EN_CURSO);

        List<TrimestreEntity> trimestres = trimestreRepo.findByCompetenciaId(competencia.getId());
        assertThat(trimestres).hasSize(4);
        TrimestreEntity q1 = trimestres.stream()
                .filter(t -> t.getNumero() == 1)
                .findFirst()
                .orElseThrow();
        assertThat(q1.getEstado()).isEqualTo(TrimestreStateMachine.PENDIENTE);

        // -------------------------------------------------------------------
        // 5. Abrir Q1 — esto debe disparar BotDecisionService internamente
        // -------------------------------------------------------------------
        TrimestreEntity q1Abierto = trimestreService.abrir(q1.getId());
        assertThat(q1Abierto.getEstado()).isEqualTo(TrimestreStateMachine.ABIERTO_DECISIONES);

        // -------------------------------------------------------------------
        // 6. Verificar que cada equipo BOT tiene una decisión ENVIADA y que
        //    los equipos humanos no tienen decisión todavía.
        // -------------------------------------------------------------------
        Long systemBotUserId = usuarioRepo.findByEmail("system-bot@simulador.local")
                .map(UsuarioEntity::getId)
                .orElseThrow(() -> new AssertionError(
                        "system-bot user no fue seeded (V202605121201)"));

        Optional<DecisionEquipoEntity> bot1Decision = decisionRepo
                .findByEquipoIdAndTrimestreId(bot1.getId(), q1.getId());
        Optional<DecisionEquipoEntity> bot2Decision = decisionRepo
                .findByEquipoIdAndTrimestreId(bot2.getId(), q1.getId());

        assertThat(bot1Decision)
                .as("Bot FACIL debe tener decisión auto-generada al abrir Q1")
                .isPresent();
        assertThat(bot2Decision)
                .as("Bot DIFICIL debe tener decisión auto-generada al abrir Q1")
                .isPresent();

        DecisionEquipoEntity d1 = bot1Decision.get();
        DecisionEquipoEntity d2 = bot2Decision.get();

        assertThat(d1.getEstado()).isEqualTo("ENVIADA");
        assertThat(d2.getEstado()).isEqualTo("ENVIADA");
        assertThat(d1.getRegistradoPorUsuarioId()).isEqualTo(systemBotUserId);
        assertThat(d2.getRegistradoPorUsuarioId()).isEqualTo(systemBotUserId);
        assertThat(d1.getPrecioVenta()).isPositive();
        assertThat(d2.getPrecioVenta()).isPositive();
        assertThat(d1.getProduccionPlanificada()).isPositive();
        assertThat(d2.getProduccionPlanificada()).isPositive();
        assertThat(d1.getSubmittedAt()).isNotNull();
        assertThat(d2.getSubmittedAt()).isNotNull();

        // Humanos NO deben tener decisión todavía (las generan ellos manualmente).
        assertThat(decisionRepo.findByEquipoIdAndTrimestreId(humano1.getId(), q1.getId()))
                .as("Equipo humano1 no debería tener decisión auto-generada")
                .isEmpty();
        assertThat(decisionRepo.findByEquipoIdAndTrimestreId(humano2.getId(), q1.getId()))
                .as("Equipo humano2 no debería tener decisión auto-generada")
                .isEmpty();
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private EquipoEntity crearHumano(Long competenciaId, String nombre, String color) {
        EquipoCreate ec = new EquipoCreate();
        ec.setNombreEmpresa(nombre);
        ec.setCodigoColor(color);
        return equipoService.create(competenciaId, ec);
    }

    private Long seedEntidadId() {
        // Cualquier entidad seed sirve; usamos la del moderador seed (RTL-2026A) implícitamente
        // resolvida via la entidad del usuario moderador. Si el seed cambia, buscamos por nombre.
        return jdbcQueryLong(
                "SELECT entidad_id FROM sim.competencia WHERE codigo = 'RTL-2026A' LIMIT 1");
    }

    private Long seedRubroId() {
        return jdbcQueryLong(
                "SELECT id FROM sim.rubro WHERE codigo = 'RETAIL_CONV' LIMIT 1");
    }

    private Long seedMacroId() {
        return jdbcQueryLong(
                "SELECT id FROM sim.parametro_macro WHERE nombre_set = 'PY_2026_BASE' LIMIT 1");
    }

    private Long seedRubroParamId() {
        return jdbcQueryLong(
                "SELECT id FROM sim.parametro_rubro WHERE codigo = 'RETAIL_CONV_BASE_2026' LIMIT 1");
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Long jdbcQueryLong(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}

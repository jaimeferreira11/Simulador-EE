package py.simulador.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import py.simulador.IntegrationTestBase;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static py.simulador.trimestre.TrimestreStateMachine.ABIERTO_DECISIONES;

/**
 * Integration tests for {@link DemoBootstrapService}.
 *
 * <p>Uses Postgres via Testcontainers so Flyway-seeded DEMO data is present and
 * the full service graph (TrimestreService, BotDecisionService) is wired.
 */
class DemoBootstrapServiceTest extends IntegrationTestBase {

    @Autowired private DemoBootstrapService bootstrap;
    @Autowired private DemoService demoService;
    @Autowired private CompetenciaRepository compRepo;
    @Autowired private TrimestreRepository trimestreRepo;

    private Long demoId;

    @BeforeEach
    void resetDemo() {
        // Resetea DEMO antes de cada test para evitar contaminación entre clases
        // (otro test puede haber finalizado la competencia o cerrado trimestres).
        demoId = compRepo.findByCodigo(DemoConstants.COMPETENCIA_CODIGO).orElseThrow().getId();
        demoService.reiniciar(demoId);
    }

    @Test
    @DisplayName("bootstrap() abre Q1 si está PENDIENTE")
    void bootstrap_abreQ1SiEstaPendiente() {
        TrimestreEntity q1 = trimestreRepo
                .findByCompetenciaIdAndNumero(demoId, (short) 1).orElseThrow();

        // Después de reiniciar, Q1 está ABIERTO_DECISIONES. Lo forzamos a PENDIENTE
        // para verificar que el bootstrap lo abre. Los snapshots INICIO ya existen
        // (creados por reiniciar) — el bootstrap los detecta y no los duplica.
        q1.setEstado("PENDIENTE");
        trimestreRepo.save(q1);

        bootstrap.bootstrap();

        TrimestreEntity q1After = trimestreRepo.findById(q1.getId()).orElseThrow();
        assertThat(q1After.getEstado()).isEqualTo(ABIERTO_DECISIONES);
    }

    @Test
    @DisplayName("bootstrap() es no-op si Q1 ya está ABIERTO_DECISIONES")
    void bootstrap_noHaceNadaSiQ1YaEstaAbierto() {
        TrimestreEntity q1 = trimestreRepo
                .findByCompetenciaIdAndNumero(demoId, (short) 1).orElseThrow();
        // reiniciar ya lo dejó ABIERTO_DECISIONES; reafirmamos por claridad.
        assertThat(q1.getEstado()).isEqualTo(ABIERTO_DECISIONES);

        bootstrap.bootstrap();   // should be a no-op

        TrimestreEntity q1After = trimestreRepo.findById(q1.getId()).orElseThrow();
        assertThat(q1After.getEstado()).isEqualTo(ABIERTO_DECISIONES);
    }
}

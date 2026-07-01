package py.simulador.demo;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.competencia.CompetenciaService;
import py.simulador.equipo.EquipoRepository;
import py.simulador.resultado.SnapshotEstadoRepository;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;
import py.simulador.trimestre.TrimestreService;

import java.util.Optional;

/**
 * Opens Q1 of the DEMO competencia at application startup if it is still in
 * {@code PENDIENTE}. Idempotent: skips silently if DEMO is not seeded, Q1 is
 * missing, or Q1 is already open / closed / processed.
 *
 * <p>Why this exists: the seed migration leaves trimestres in {@code PENDIENTE}
 * because opening one requires business logic (state machine + bot decision
 * generation) that lives in Java, not SQL. This service ensures the demo is
 * ready to use immediately after app startup with no manual "Reiniciar" first.
 *
 * <p>The {@code @PostConstruct} runs after Flyway migrations and JPA bean
 * initialization (repositories depend on the {@code EntityManagerFactory},
 * which depends on Flyway). {@link TrimestreService#abrir(Long)} provides its
 * own transactional boundary, so self-invocation is not an issue here.
 *
 * <p>Before opening Q1 we create the INICIO snapshots for each team — the
 * motor depends on those at close time, and the seed leaves them missing
 * because the competencia starts already in {@code EN_CURSO} (it does not
 * pass through {@code CompetenciaService.iniciar}).
 */
@Component
public class DemoBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(DemoBootstrapService.class);

    private final CompetenciaRepository compRepo;
    private final CompetenciaService competenciaService;
    private final EquipoRepository equipoRepo;
    private final TrimestreRepository trimestreRepo;
    private final TrimestreService trimestreService;
    private final SnapshotEstadoRepository snapshotRepo;

    public DemoBootstrapService(CompetenciaRepository compRepo,
                                CompetenciaService competenciaService,
                                EquipoRepository equipoRepo,
                                TrimestreRepository trimestreRepo,
                                TrimestreService trimestreService,
                                SnapshotEstadoRepository snapshotRepo) {
        this.compRepo = compRepo;
        this.competenciaService = competenciaService;
        this.equipoRepo = equipoRepo;
        this.trimestreRepo = trimestreRepo;
        this.trimestreService = trimestreService;
        this.snapshotRepo = snapshotRepo;
    }

    @PostConstruct
    public void bootstrap() {
        try {
            var compOpt = compRepo.findByCodigo(DemoConstants.COMPETENCIA_CODIGO);
            if (compOpt.isEmpty()) {
                log.info("Competencia DEMO no encontrada en seed — bootstrap omitido.");
                return;
            }
            var comp = compOpt.get();

            Optional<TrimestreEntity> q1Opt =
                    trimestreRepo.findByCompetenciaIdAndNumero(comp.getId(), (short) 1);
            if (q1Opt.isEmpty()) {
                log.warn("Q1 de DEMO no existe — bootstrap omitido.");
                return;
            }
            TrimestreEntity q1 = q1Opt.get();

            if (!"PENDIENTE".equals(q1.getEstado())) {
                log.info("Q1 de DEMO ya está en {} — bootstrap omitido.", q1.getEstado());
                return;
            }

            // Si Q1 está PENDIENTE pero los snapshots INICIO ya existen (p. ej.
            // un reset previo dejó los snapshots — no debería pasar, pero
            // defendemos el invariante), saltamos su creación para no romper
            // la UNIQUE (equipo_id, trimestre_id, momento).
            boolean hasSnapshots = !snapshotRepo
                    .findByTrimestreIdAndMomento(q1.getId(), "INICIO").isEmpty();
            if (!hasSnapshots) {
                log.info("DemoBootstrapService: creando snapshots INICIO de Q1 de DEMO");
                competenciaService.crearSnapshotsInicialesQ1(
                        comp, equipoRepo.findByCompetenciaId(comp.getId()), q1);
            }

            log.info("DemoBootstrapService: abriendo Q1 de DEMO (id={})", q1.getId());
            trimestreService.abrir(q1.getId());
        } catch (Exception ex) {
            // Don't fail app startup if demo seed is missing or in an odd state.
            log.warn("DemoBootstrapService omitido por excepción: {}", ex.getMessage());
        }
    }
}

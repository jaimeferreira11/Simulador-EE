package py.simulador.resultado;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.auditoria.AuditoriaEventoEntity;
import py.simulador.auditoria.AuditoriaEventoRepository;
import py.simulador.catalogo.EventoCatalogoEntity;
import py.simulador.catalogo.EventoCatalogoRepository;
import py.simulador.common.ResourceNotFoundException;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.decision.DecisionEquipoEntity;
import py.simulador.decision.DecisionEquipoRepository;
import py.simulador.equipo.EquipoEntity;
import py.simulador.equipo.EquipoRepository;
import py.simulador.evento.EventoCompetenciaEntity;
import py.simulador.evento.EventoCompetenciaRepository;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio de exportación PDF. Carga toda la información necesaria de la base
 * de datos (en una transacción de solo lectura) y delega el render a
 * {@link ReportePdfBuilder}.
 *
 * <p>El reporte producido es el "Reporte Final Comercial": portada + ranking
 * con podio resaltado + eventos destacados + una página por equipo con
 * métricas y mini-charts de evolución (PIP / share). Las secciones
 * {@code decisiones} y {@code bitacora} siguen disponibles como apéndices
 * tabulares.
 *
 * <p>Endpoints servidos: {@code GET /v1/competencias/{id}/export/pdf}
 * (controller en {@link ResultadoController}).
 */
@Service
public class PdfExportService {

    private final CompetenciaRepository competenciaRepo;
    private final EquipoRepository equipoRepo;
    private final TrimestreRepository trimestreRepo;
    private final RankingTrimestreRepository rankingRepo;
    private final ResultadoCalculoRepository resultadoRepo;
    private final SnapshotEstadoRepository snapshotRepo;
    private final EventoCompetenciaRepository eventoRepo;
    private final EventoCatalogoRepository catalogoRepo;
    private final DecisionEquipoRepository decisionRepo;
    private final AuditoriaEventoRepository auditoriaRepo;

    public PdfExportService(CompetenciaRepository competenciaRepo,
                            EquipoRepository equipoRepo,
                            TrimestreRepository trimestreRepo,
                            RankingTrimestreRepository rankingRepo,
                            ResultadoCalculoRepository resultadoRepo,
                            SnapshotEstadoRepository snapshotRepo,
                            EventoCompetenciaRepository eventoRepo,
                            EventoCatalogoRepository catalogoRepo,
                            DecisionEquipoRepository decisionRepo,
                            AuditoriaEventoRepository auditoriaRepo) {
        this.competenciaRepo = competenciaRepo;
        this.equipoRepo = equipoRepo;
        this.trimestreRepo = trimestreRepo;
        this.rankingRepo = rankingRepo;
        this.resultadoRepo = resultadoRepo;
        this.snapshotRepo = snapshotRepo;
        this.eventoRepo = eventoRepo;
        this.catalogoRepo = catalogoRepo;
        this.decisionRepo = decisionRepo;
        this.auditoriaRepo = auditoriaRepo;
    }

    @Transactional(readOnly = true)
    public byte[] exportar(Long competenciaId) throws IOException {
        return exportar(competenciaId, Set.of("ranking", "resultados", "eventos"));
    }

    @Transactional(readOnly = true)
    public byte[] exportar(Long competenciaId, Set<String> sections) throws IOException {
        ReporteData data = cargar(competenciaId);
        return new ReportePdfBuilder().build(data, sections);
    }

    /**
     * Carga toda la data de la competencia y la convierte a {@link ReporteData}.
     * Visibility package para permitir reuso desde tests sin remontar el
     * pipeline.
     */
    ReporteData cargar(Long competenciaId) {
        CompetenciaEntity comp = competenciaRepo.findById(competenciaId)
                .orElseThrow(() -> new ResourceNotFoundException("Competencia", competenciaId));

        List<EquipoEntity> equipos = equipoRepo.findByCompetenciaId(competenciaId);
        Map<Long, EquipoEntity> equiposPorId = equipos.stream()
                .collect(Collectors.toMap(EquipoEntity::getId, e -> e));

        List<TrimestreEntity> trimestres = trimestreRepo.findByCompetenciaId(competenciaId);
        List<TrimestreEntity> procesados = trimestres.stream()
                .filter(t -> "PROCESADO".equals(t.getEstado())).toList();
        Map<Long, Short> triNumeros = trimestres.stream()
                .collect(Collectors.toMap(TrimestreEntity::getId, TrimestreEntity::getNumero));

        // --- Info de competencia ---
        ReporteData.CompetenciaInfo compInfo = new ReporteData.CompetenciaInfo(
                comp.getId(),
                comp.getCodigo(),
                comp.getNombre(),
                comp.getEstado(),
                comp.getNumTrimestres(),
                comp.getInicioAt(),
                comp.getCierreAt()
        );

        // --- Trimestres procesados ---
        List<ReporteData.TrimestreInfo> triInfos = procesados.stream()
                .map(t -> new ReporteData.TrimestreInfo(t.getId(), t.getNumero(), t.getEstado()))
                .toList();

        // --- Ranking final + ganador ---
        List<ReporteData.RankingItem> rankingFinal = new ArrayList<>();
        Integer numUltQ = null;
        String ganador = null;

        if (!procesados.isEmpty()) {
            TrimestreEntity ult = procesados.get(procesados.size() - 1);
            numUltQ = (int) ult.getNumero();
            List<RankingTrimestreEntity> ranking = rankingRepo
                    .findByCompetenciaIdAndTrimestreId(competenciaId, ult.getId());
            for (RankingTrimestreEntity r : ranking) {
                EquipoEntity eq = equiposPorId.get(r.getEquipoId());
                String nombre = eq != null ? eq.getNombreEmpresa() : "?";
                boolean esBot = eq != null && eq.esBot();
                if (r.getPosicion() == 1) ganador = nombre;
                rankingFinal.add(new ReporteData.RankingItem(
                        r.getPosicion(), r.getEquipoId(), nombre, esBot,
                        r.getPipAcumulado(), r.getUtilidadAcumulada(),
                        r.getCajaActual(), r.getShareActual()));
            }
        }

        // --- Métricas por equipo (para las páginas de equipo) ---
        // Pre-cargamos resultados y rankings por trimestre en mapas para evitar N+1.
        Map<Long, List<ResultadoCalculoEntity>> resultadosPorTri = new HashMap<>();
        Map<Long, List<RankingTrimestreEntity>> rankingsPorTri = new HashMap<>();
        Map<Long, List<SnapshotEstadoEntity>> snapsPorTri = new HashMap<>();
        for (TrimestreEntity t : procesados) {
            resultadosPorTri.put(t.getId(), resultadoRepo.findByTrimestreId(t.getId()));
            rankingsPorTri.put(t.getId(),
                    rankingRepo.findByCompetenciaIdAndTrimestreId(competenciaId, t.getId()));
            snapsPorTri.put(t.getId(), snapshotRepo.findByTrimestreIdAndMomento(t.getId(), "CIERRE"));
        }

        List<ReporteData.EquipoReporte> equipoReportes = new ArrayList<>();
        for (EquipoEntity eq : equipos) {
            List<ReporteData.TrimestreMetric> metricas = new ArrayList<>();
            Integer posFinal = null;
            for (TrimestreEntity t : procesados) {
                ResultadoCalculoEntity res = findResultadoFor(resultadosPorTri.get(t.getId()), eq.getId());
                Integer pos = findPosicionFor(rankingsPorTri.get(t.getId()), eq.getId());
                long caja = findCajaFor(snapsPorTri.get(t.getId()), eq.getId());
                if (res == null) {
                    // sin resultado en este Q (puede pasar si el equipo se sumó tarde)
                    metricas.add(new ReporteData.TrimestreMetric(
                            t.getNumero(), pos, null, 0L, 0L, null, caja));
                } else {
                    metricas.add(new ReporteData.TrimestreMetric(
                            t.getNumero(), pos, res.getPipTrimestre(),
                            res.getIngresos(), res.getUtilidadNeta(),
                            res.getShare(), caja));
                }
                if (pos != null) posFinal = pos; // último Q con ranking
            }
            equipoReportes.add(new ReporteData.EquipoReporte(
                    eq.getId(),
                    eq.getNombreEmpresa(),
                    eq.esBot(),
                    eq.getDificultad(),
                    eq.getPersonalidad(),
                    posFinal,
                    metricas));
        }
        // Ordenar equipos por posición final (los sin posición van al final)
        equipoReportes.sort((a, b) -> {
            Integer pa = a.posicionFinal();
            Integer pb = b.posicionFinal();
            if (pa == null && pb == null) return 0;
            if (pa == null) return 1;
            if (pb == null) return -1;
            return Integer.compare(pa, pb);
        });

        // --- Eventos ---
        List<ReporteData.EventoReporte> eventoReportes = new ArrayList<>();
        List<EventoCompetenciaEntity> eventos = eventoRepo.findByCompetenciaId(competenciaId);
        Map<Long, EventoCatalogoEntity> catalogoCache = new HashMap<>();
        for (EventoCompetenciaEntity e : eventos) {
            EventoCatalogoEntity cat = catalogoCache.computeIfAbsent(
                    e.getEventoCatalogoId(),
                    id -> catalogoRepo.findById(id).orElse(null));
            Short num = triNumeros.get(e.getTrimestreId());
            eventoReportes.add(new ReporteData.EventoReporte(
                    num != null ? num : 0,
                    cat != null ? cat.getNombre() : ("ID:" + e.getEventoCatalogoId()),
                    cat != null ? cat.getSeveridad() : null,
                    e.getOrigen(),
                    e.getMagnitudAplicada(),
                    e.getDuracionAplicada()));
        }

        // --- Decisiones ---
        List<ReporteData.DecisionReporte> decisionReportes = new ArrayList<>();
        for (TrimestreEntity t : trimestres) {
            List<DecisionEquipoEntity> decs = decisionRepo.findByTrimestreId(t.getId());
            for (DecisionEquipoEntity d : decs) {
                EquipoEntity eq = equiposPorId.get(d.getEquipoId());
                decisionReportes.add(new ReporteData.DecisionReporte(
                        t.getNumero(),
                        eq != null ? eq.getNombreEmpresa() : "?",
                        d.getPrecioVenta(),
                        d.getProduccionPlanificada(),
                        d.getInversionMarketing(),
                        d.getInversionCapacidad(),
                        d.getContratacionesNetas(),
                        d.getInversionId(),
                        d.getPrestamoSolicitado(),
                        d.getDividendosPagar()));
            }
        }

        // --- Auditoría ---
        List<AuditoriaEventoEntity> registros = auditoriaRepo.findByCompetenciaId(competenciaId);
        List<ReporteData.AuditoriaReporte> auditoriaReportes = registros.stream()
                .map(a -> new ReporteData.AuditoriaReporte(
                        a.getOcurridoAt(), a.getTipoAccion(), a.getDescripcion()))
                .toList();

        // Mantener orden estable
        return new ReporteData(
                compInfo,
                triInfos,
                rankingFinal,
                numUltQ,
                ganador,
                equipoReportes,
                eventoReportes,
                decisionReportes,
                auditoriaReportes);
    }

    // --- Helpers de búsqueda ---

    private static ResultadoCalculoEntity findResultadoFor(List<ResultadoCalculoEntity> list, Long equipoId) {
        if (list == null) return null;
        for (ResultadoCalculoEntity r : list) {
            if (r.getEquipoId().equals(equipoId)) return r;
        }
        return null;
    }

    private static Integer findPosicionFor(List<RankingTrimestreEntity> list, Long equipoId) {
        if (list == null) return null;
        for (RankingTrimestreEntity r : list) {
            if (r.getEquipoId().equals(equipoId)) return (int) r.getPosicion();
        }
        return null;
    }

    private static long findCajaFor(List<SnapshotEstadoEntity> list, Long equipoId) {
        if (list == null) return 0L;
        for (SnapshotEstadoEntity s : list) {
            if (s.getEquipoId().equals(equipoId)) return s.getCaja();
        }
        return 0L;
    }

    /**
     * Mantiene compatibilidad con tests / cargas que usaban el orden de
     * inserción original. Mantenemos un LinkedHashMap interno aunque el método
     * no se exporta.
     */
    @SuppressWarnings("unused")
    private static <K, V> LinkedHashMap<K, V> linkedMap() {
        return new LinkedHashMap<>();
    }
}

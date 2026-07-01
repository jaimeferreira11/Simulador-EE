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

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CsvExportService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    public CsvExportService(CompetenciaRepository competenciaRepo,
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
    public byte[] exportar(Long competenciaId, Set<String> sections) {
        CompetenciaEntity comp = competenciaRepo.findById(competenciaId)
                .orElseThrow(() -> new ResourceNotFoundException("Competencia", competenciaId));

        List<EquipoEntity> equipos = equipoRepo.findByCompetenciaId(competenciaId);
        Map<Long, String> equipoNombres = equipos.stream()
                .collect(Collectors.toMap(EquipoEntity::getId, EquipoEntity::getNombreEmpresa));

        List<TrimestreEntity> trimestres = trimestreRepo.findByCompetenciaId(competenciaId);
        List<TrimestreEntity> procesados = trimestres.stream()
                .filter(t -> "PROCESADO".equals(t.getEstado())).toList();

        StringBuilder sb = new StringBuilder();

        if (sections.contains("ranking")) {
            appendRanking(sb, comp, equipoNombres, procesados);
        }
        if (sections.contains("resultados")) {
            appendResultados(sb, equipoNombres, procesados);
        }
        if (sections.contains("eventos")) {
            appendEventos(sb, competenciaId, trimestres);
        }
        if (sections.contains("decisiones")) {
            appendDecisiones(sb, equipoNombres, trimestres);
        }
        if (sections.contains("bitacora")) {
            appendBitacora(sb, competenciaId);
        }

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private void appendRanking(StringBuilder sb, CompetenciaEntity comp,
                               Map<Long, String> equipoNombres,
                               List<TrimestreEntity> procesados) {
        sb.append("## RANKING\n");
        if (procesados.isEmpty()) {
            sb.append("No hay trimestres procesados\n\n");
            return;
        }
        TrimestreEntity ultimo = procesados.get(procesados.size() - 1);
        List<RankingTrimestreEntity> rankings = rankingRepo
                .findByCompetenciaIdAndTrimestreId(comp.getId(), ultimo.getId());

        sb.append("Posicion,Equipo,PIP Acumulado,Utilidad Acumulada,Caja,Share\n");
        for (RankingTrimestreEntity r : rankings) {
            sb.append(r.getPosicion()).append(',');
            sb.append(esc(equipoNombres.getOrDefault(r.getEquipoId(), "?"))).append(',');
            sb.append(fmtDec(r.getPipAcumulado())).append(',');
            sb.append(r.getUtilidadAcumulada()).append(',');
            sb.append(r.getCajaActual()).append(',');
            sb.append(fmtDec(r.getShareActual())).append('\n');
        }
        sb.append('\n');
    }

    private void appendResultados(StringBuilder sb, Map<Long, String> equipoNombres,
                                  List<TrimestreEntity> procesados) {
        sb.append("## RESULTADOS POR TRIMESTRE\n");
        sb.append("Q,Equipo,Produccion,Demanda,Ventas,Ingresos,Costos Totales,Utilidad Operativa,Impuesto IRE,Utilidad Neta,Share,PIP\n");
        for (TrimestreEntity tri : procesados) {
            List<ResultadoCalculoEntity> resultados = resultadoRepo.findByTrimestreId(tri.getId());
            for (ResultadoCalculoEntity r : resultados) {
                sb.append("Q").append(tri.getNumero()).append(',');
                sb.append(esc(equipoNombres.getOrDefault(r.getEquipoId(), "?"))).append(',');
                sb.append(r.getProduccionReal()).append(',');
                sb.append(r.getDemandaAsignada()).append(',');
                sb.append(r.getVentasUnidades()).append(',');
                sb.append(r.getIngresos()).append(',');
                sb.append(r.getCostosOperativosTotal()).append(',');
                sb.append(r.getUtilidadOperativa()).append(',');
                sb.append(r.getImpuestoIre()).append(',');
                sb.append(r.getUtilidadNeta()).append(',');
                sb.append(fmtDec(r.getShare())).append(',');
                sb.append(fmtDec(r.getPipTrimestre())).append('\n');
            }
        }
        sb.append('\n');
    }

    private void appendEventos(StringBuilder sb, Long competenciaId,
                               List<TrimestreEntity> trimestres) {
        sb.append("## HISTORIAL DE EVENTOS\n");

        Map<Long, Short> triNumeros = trimestres.stream()
                .collect(Collectors.toMap(TrimestreEntity::getId, TrimestreEntity::getNumero));

        List<EventoCompetenciaEntity> eventos = eventoRepo.findByCompetenciaId(competenciaId);

        // Cache catalog names
        Map<Long, String> catalogoNombres = new java.util.HashMap<>();
        for (EventoCompetenciaEntity e : eventos) {
            if (!catalogoNombres.containsKey(e.getEventoCatalogoId())) {
                catalogoRepo.findById(e.getEventoCatalogoId())
                        .ifPresent(c -> catalogoNombres.put(c.getId(), c.getNombre()));
            }
        }

        sb.append("Q,Evento,Origen,Magnitud,Duracion,Justificacion,Fecha\n");
        for (EventoCompetenciaEntity e : eventos) {
            Short num = triNumeros.get(e.getTrimestreId());
            sb.append("Q").append(num != null ? num : "?").append(',');
            sb.append(esc(catalogoNombres.getOrDefault(e.getEventoCatalogoId(), "ID:" + e.getEventoCatalogoId()))).append(',');
            sb.append(e.getOrigen()).append(',');
            sb.append(fmtDec(e.getMagnitudAplicada())).append(',');
            sb.append(e.getDuracionAplicada()).append(',');
            sb.append(esc(e.getJustificacion() != null ? e.getJustificacion() : "")).append(',');
            sb.append(e.getCreatedAt() != null ? e.getCreatedAt().format(DT_FMT) : "").append('\n');
        }
        sb.append('\n');
    }

    private void appendDecisiones(StringBuilder sb, Map<Long, String> equipoNombres,
                                  List<TrimestreEntity> trimestres) {
        sb.append("## HISTORIAL DE DECISIONES\n");
        sb.append("Q,Equipo,Estado,Precio Venta,Produccion,Marketing,Inv. Capacidad,Contrataciones,Aumento Salarial %,Capacitacion,I+D,Prestamo,Dividendos\n");
        for (TrimestreEntity tri : trimestres) {
            List<DecisionEquipoEntity> decisiones = decisionRepo.findByTrimestreId(tri.getId());
            for (DecisionEquipoEntity d : decisiones) {
                sb.append("Q").append(tri.getNumero()).append(',');
                sb.append(esc(equipoNombres.getOrDefault(d.getEquipoId(), "?"))).append(',');
                sb.append(d.getEstado()).append(',');
                sb.append(d.getPrecioVenta()).append(',');
                sb.append(d.getProduccionPlanificada()).append(',');
                sb.append(d.getInversionMarketing()).append(',');
                sb.append(d.getInversionCapacidad()).append(',');
                sb.append(d.getContratacionesNetas()).append(',');
                sb.append(fmtDec(d.getAumentoSalarialPct())).append(',');
                sb.append(d.getInversionCapacitacion()).append(',');
                sb.append(d.getInversionId()).append(',');
                sb.append(d.getPrestamoSolicitado()).append(',');
                sb.append(d.getDividendosPagar()).append('\n');
            }
        }
        sb.append('\n');
    }

    private void appendBitacora(StringBuilder sb, Long competenciaId) {
        sb.append("## BITACORA AUDITORIA\n");
        List<AuditoriaEventoEntity> registros = auditoriaRepo.findByCompetenciaId(competenciaId);
        sb.append("Fecha,Tipo Accion,Descripcion\n");
        for (AuditoriaEventoEntity a : registros) {
            sb.append(a.getOcurridoAt() != null ? a.getOcurridoAt().format(DT_FMT) : "").append(',');
            sb.append(esc(a.getTipoAccion())).append(',');
            sb.append(esc(a.getDescripcion() != null ? a.getDescripcion() : "")).append('\n');
        }
        sb.append('\n');
    }

    private String esc(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String fmtDec(BigDecimal value) {
        return value != null ? value.toPlainString() : "0";
    }
}

package py.simulador.resultado;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import py.simulador.api.generated.ResultadosApi;
import py.simulador.api.generated.model.*;
import py.simulador.equipo.EquipoEntity;
import py.simulador.llm.CoachingTrimestreEntity;
import py.simulador.llm.CoachingTrimestreRepository;

import java.io.IOException;
import java.util.*;

@RestController
public class ResultadoController implements ResultadosApi {

    private static final Set<String> DEFAULT_SECTIONS = Set.of("ranking", "resultados", "eventos");

    private final ResultadoService service;
    private final ResultadoMapper mapper;
    private final ExcelExportService excelExportService;
    private final PdfExportService pdfExportService;
    private final CsvExportService csvExportService;
    private final CoachingTrimestreRepository coachingRepo;

    public ResultadoController(ResultadoService service, ResultadoMapper mapper,
                               ExcelExportService excelExportService,
                               PdfExportService pdfExportService,
                               CsvExportService csvExportService,
                               CoachingTrimestreRepository coachingRepo) {
        this.service = service;
        this.mapper = mapper;
        this.excelExportService = excelExportService;
        this.pdfExportService = pdfExportService;
        this.csvExportService = csvExportService;
        this.coachingRepo = coachingRepo;
    }

    private Set<String> parseSections(List<String> sections) {
        if (sections == null || sections.isEmpty()) return DEFAULT_SECTIONS;
        return new HashSet<>(sections);
    }

    @Override
    public ResponseEntity<ResultadoCalculo> equiposEquipoIdTrimestresTrimestreIdResultadoGet(
            Long equipoId, Long trimestreId) {
        return ResponseEntity.ok(mapper.toResultadoDto(service.findResultado(equipoId, trimestreId)));
    }

    @Override
    public ResponseEntity<List<SnapshotEstado>> equiposEquipoIdTrimestresTrimestreIdSnapshotGet(
            Long equipoId, Long trimestreId, String momento) {
        // El API retorna lista pero en la practica es un solo snapshot por momento
        SnapshotEstadoEntity entity = service.findSnapshot(equipoId, trimestreId,
                momento != null ? momento : "CIERRE");
        return ResponseEntity.ok(List.of(mapper.toSnapshotDto(entity)));
    }

    @Override
    public ResponseEntity<List<RankingItem>> competenciasCompetenciaIdRankingGet(
            Long competenciaId, Long trimestreId) {
        List<RankingTrimestreEntity> rankings = service.findRanking(competenciaId, trimestreId);
        Map<Long, EquipoEntity> equipos = service.equiposPorCompetencia(competenciaId);

        List<RankingItem> dtos = rankings.stream().map(r -> {
            RankingItem dto = mapper.toRankingDto(r);
            EquipoEntity eq = equipos.get(r.getEquipoId());
            if (eq != null) {
                dto.setNombreEmpresa(eq.getNombreEmpresa());
                dto.setCodigoColor(eq.getCodigoColor());
            }
            return dto;
        }).toList();

        return ResponseEntity.ok(dtos);
    }

    @Override
    public ResponseEntity<List<EvolucionEquipo>> competenciasCompetenciaIdRankingEvolucionGet(
            Long competenciaId) {
        List<RankingTrimestreEntity> allRankings = service.findAllRankings(competenciaId);
        Map<Long, EquipoEntity> equipos = service.equiposPorCompetencia(competenciaId);
        Map<Long, Short> triNumeros = service.trimestreNumeros(competenciaId);

        // Agrupar por equipoId manteniendo orden
        Map<Long, List<RankingTrimestreEntity>> porEquipo = new LinkedHashMap<>();
        for (RankingTrimestreEntity r : allRankings) {
            porEquipo.computeIfAbsent(r.getEquipoId(), k -> new ArrayList<>()).add(r);
        }

        List<EvolucionEquipo> resultado = porEquipo.entrySet().stream().map(entry -> {
            Long equipoId = entry.getKey();
            EvolucionEquipo evo = new EvolucionEquipo();
            evo.setEquipoId(equipoId);
            EquipoEntity eq = equipos.get(equipoId);
            if (eq != null) {
                evo.setNombreEmpresa(eq.getNombreEmpresa());
                evo.setCodigoColor(eq.getCodigoColor());
            }
            List<EvolucionEquipoSeriePipInner> serie = entry.getValue().stream().map(r -> {
                EvolucionEquipoSeriePipInner punto = new EvolucionEquipoSeriePipInner();
                Short num = triNumeros.get(r.getTrimestreId());
                punto.setTrimestre(num != null ? num.intValue() : 0);
                punto.setPip(r.getPipAcumulado() != null ? r.getPipAcumulado().floatValue() : 0f);
                return punto;
            }).toList();
            evo.setSeriePip(serie);
            return evo;
        }).toList();

        return ResponseEntity.ok(resultado);
    }

    @Override
    public ResponseEntity<Resource> competenciasCompetenciaIdExportExcelGet(Long competenciaId) {
        try {
            byte[] bytes = excelExportService.exportar(competenciaId);
            ByteArrayResource resource = new ByteArrayResource(bytes);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=competencia_" + competenciaId + ".xlsx")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(bytes.length)
                    .body(resource);
        } catch (IOException e) {
            throw new RuntimeException("Error generando Excel", e);
        }
    }

    @GetMapping("/competencias/{competenciaId}/export/pdf")
    public ResponseEntity<Resource> exportPdf(
            @PathVariable Long competenciaId,
            @RequestParam(required = false) List<String> sections) {
        try {
            byte[] bytes = pdfExportService.exportar(competenciaId, parseSections(sections));
            ByteArrayResource resource = new ByteArrayResource(bytes);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=competencia_" + competenciaId + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(bytes.length)
                    .body(resource);
        } catch (IOException e) {
            throw new RuntimeException("Error generando PDF", e);
        }
    }

    @GetMapping("/competencias/{competenciaId}/export/csv")
    public ResponseEntity<Resource> exportCsv(
            @PathVariable Long competenciaId,
            @RequestParam(required = false) List<String> sections) {
        byte[] bytes = csvExportService.exportar(competenciaId, parseSections(sections));
        ByteArrayResource resource = new ByteArrayResource(bytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=competencia_" + competenciaId + ".csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(bytes.length)
                .body(resource);
    }

    @Override
    public ResponseEntity<CoachingTrimestre> equiposEquipoIdTrimestresTrimestreIdCoachingGet(
            Long equipoId, Long trimestreId) {
        return coachingRepo.findByTrimestreIdAndEquipoId(trimestreId, equipoId)
                .map(e -> {
                    CoachingTrimestre dto = new CoachingTrimestre();
                    dto.setId(e.getId());
                    dto.setTrimestreId(e.getTrimestreId());
                    dto.setEquipoId(e.getEquipoId());
                    dto.setTexto(e.getTexto());
                    dto.setCreatedAt(e.getCreatedAt());
                    return ResponseEntity.ok(dto);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
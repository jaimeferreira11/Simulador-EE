package py.simulador.resultado;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.common.ResourceNotFoundException;
import py.simulador.equipo.EquipoEntity;
import py.simulador.equipo.EquipoRepository;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Genera un archivo Excel (.xlsx) con los resultados completos de una competencia:
 * - Hoja Ranking: ranking final con PIP, utilidad, caja, share
 * - Hoja Resultados: resultado_calculo por trimestre y equipo
 * - Hoja Snapshots: snapshot_estado CIERRE por trimestre y equipo
 */
@Service
public class ExcelExportService {

    private final CompetenciaRepository competenciaRepo;
    private final EquipoRepository equipoRepo;
    private final TrimestreRepository trimestreRepo;
    private final RankingTrimestreRepository rankingRepo;
    private final ResultadoCalculoRepository resultadoRepo;
    private final SnapshotEstadoRepository snapshotRepo;

    public ExcelExportService(CompetenciaRepository competenciaRepo,
                              EquipoRepository equipoRepo,
                              TrimestreRepository trimestreRepo,
                              RankingTrimestreRepository rankingRepo,
                              ResultadoCalculoRepository resultadoRepo,
                              SnapshotEstadoRepository snapshotRepo) {
        this.competenciaRepo = competenciaRepo;
        this.equipoRepo = equipoRepo;
        this.trimestreRepo = trimestreRepo;
        this.rankingRepo = rankingRepo;
        this.resultadoRepo = resultadoRepo;
        this.snapshotRepo = snapshotRepo;
    }

    @Transactional(readOnly = true)
    public byte[] exportar(Long competenciaId) throws IOException {
        CompetenciaEntity comp = competenciaRepo.findById(competenciaId)
                .orElseThrow(() -> new ResourceNotFoundException("Competencia", competenciaId));

        List<EquipoEntity> equipos = equipoRepo.findByCompetenciaId(competenciaId);
        Map<Long, String> equipoNombres = equipos.stream()
                .collect(Collectors.toMap(EquipoEntity::getId, EquipoEntity::getNombreEmpresa));

        List<TrimestreEntity> trimestres = trimestreRepo.findByCompetenciaId(competenciaId);
        Map<Long, Short> triNumeros = trimestres.stream()
                .collect(Collectors.toMap(TrimestreEntity::getId, TrimestreEntity::getNumero));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = crearEstiloHeader(wb);

            crearHojaRanking(wb, headerStyle, comp, equipoNombres, trimestres);
            crearHojaResultados(wb, headerStyle, equipoNombres, triNumeros, trimestres);
            crearHojaSnapshots(wb, headerStyle, equipoNombres, triNumeros, trimestres);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void crearHojaRanking(XSSFWorkbook wb, CellStyle headerStyle,
                                   CompetenciaEntity comp, Map<Long, String> equipoNombres,
                                   List<TrimestreEntity> trimestres) {
        Sheet sheet = wb.createSheet("Ranking");

        // Titulo
        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue("Competencia: " + comp.getNombre() + " (" + comp.getCodigo() + ")");

        // Buscar ultimo trimestre procesado
        TrimestreEntity ultimo = trimestres.stream()
                .filter(t -> "PROCESADO".equals(t.getEstado()))
                .reduce((a, b) -> b).orElse(null);
        if (ultimo == null) {
            Row r = sheet.createRow(2);
            r.createCell(0).setCellValue("No hay trimestres procesados");
            return;
        }

        String[] headers = {"Posición", "Equipo", "PIP Acumulado", "Utilidad Acumulada", "Caja Actual", "Share Actual"};
        Row headerRow = sheet.createRow(2);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        List<RankingTrimestreEntity> rankings = rankingRepo
                .findByCompetenciaIdAndTrimestreId(comp.getId(), ultimo.getId());
        int rowIdx = 3;
        for (RankingTrimestreEntity r : rankings) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(r.getPosicion());
            row.createCell(1).setCellValue(equipoNombres.getOrDefault(r.getEquipoId(), "Equipo " + r.getEquipoId()));
            row.createCell(2).setCellValue(r.getPipAcumulado() != null ? r.getPipAcumulado().doubleValue() : 0);
            row.createCell(3).setCellValue(r.getUtilidadAcumulada());
            row.createCell(4).setCellValue(r.getCajaActual());
            row.createCell(5).setCellValue(r.getShareActual() != null ? r.getShareActual().doubleValue() : 0);
        }

        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
    }

    private void crearHojaResultados(XSSFWorkbook wb, CellStyle headerStyle,
                                      Map<Long, String> equipoNombres,
                                      Map<Long, Short> triNumeros,
                                      List<TrimestreEntity> trimestres) {
        Sheet sheet = wb.createSheet("Resultados");

        String[] headers = {"Q", "Equipo", "Producción", "Demanda", "Ventas", "Ingresos",
                "Costos Totales", "Utilidad Operativa", "Impuesto IRE", "Utilidad Neta",
                "Share", "PIP"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        for (TrimestreEntity tri : trimestres) {
            if (!"PROCESADO".equals(tri.getEstado())) continue;
            List<ResultadoCalculoEntity> resultados = resultadoRepo.findByTrimestreId(tri.getId());
            for (ResultadoCalculoEntity r : resultados) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue("Q" + tri.getNumero());
                row.createCell(1).setCellValue(equipoNombres.getOrDefault(r.getEquipoId(), "?"));
                row.createCell(2).setCellValue(r.getProduccionReal());
                row.createCell(3).setCellValue(r.getDemandaAsignada());
                row.createCell(4).setCellValue(r.getVentasUnidades());
                row.createCell(5).setCellValue(r.getIngresos());
                row.createCell(6).setCellValue(r.getCostosOperativosTotal());
                row.createCell(7).setCellValue(r.getUtilidadOperativa());
                row.createCell(8).setCellValue(r.getImpuestoIre());
                row.createCell(9).setCellValue(r.getUtilidadNeta());
                row.createCell(10).setCellValue(r.getShare() != null ? r.getShare().doubleValue() : 0);
                row.createCell(11).setCellValue(r.getPipTrimestre() != null ? r.getPipTrimestre().doubleValue() : 0);
            }
        }

        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
    }

    private void crearHojaSnapshots(XSSFWorkbook wb, CellStyle headerStyle,
                                     Map<Long, String> equipoNombres,
                                     Map<Long, Short> triNumeros,
                                     List<TrimestreEntity> trimestres) {
        Sheet sheet = wb.createSheet("Estado");

        String[] headers = {"Q", "Equipo", "Caja", "Deuda", "Patrimonio Neto", "Valor Planta",
                "Capacidad", "Headcount", "Salario", "Inventario", "Brand Equity", "Calidad", "I+D Acum", "PIP"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        for (TrimestreEntity tri : trimestres) {
            if (!"PROCESADO".equals(tri.getEstado())) continue;
            List<SnapshotEstadoEntity> snapshots = snapshotRepo
                    .findByTrimestreIdAndMomento(tri.getId(), "CIERRE");
            for (SnapshotEstadoEntity s : snapshots) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue("Q" + tri.getNumero());
                row.createCell(1).setCellValue(equipoNombres.getOrDefault(s.getEquipoId(), "?"));
                row.createCell(2).setCellValue(s.getCaja());
                row.createCell(3).setCellValue(s.getDeuda());
                row.createCell(4).setCellValue(s.getPatrimonioNeto());
                row.createCell(5).setCellValue(s.getValorPlanta());
                row.createCell(6).setCellValue(s.getCapacidad());
                row.createCell(7).setCellValue(s.getHeadcount());
                row.createCell(8).setCellValue(s.getSalario());
                row.createCell(9).setCellValue(s.getInventario());
                row.createCell(10).setCellValue(s.getBrandEquity() != null ? s.getBrandEquity().doubleValue() : 0);
                row.createCell(11).setCellValue(s.getCalidadPercibida() != null ? s.getCalidadPercibida().doubleValue() : 0);
                row.createCell(12).setCellValue(s.getIdAcumulado());
                row.createCell(13).setCellValue(s.getPip() != null ? s.getPip().doubleValue() : 0);
            }
        }

        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
    }

    private CellStyle crearEstiloHeader(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}

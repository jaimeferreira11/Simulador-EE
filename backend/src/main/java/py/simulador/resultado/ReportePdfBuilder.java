package py.simulador.resultado;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import py.simulador.resultado.ReporteData.AuditoriaReporte;
import py.simulador.resultado.ReporteData.DecisionReporte;
import py.simulador.resultado.ReporteData.EquipoReporte;
import py.simulador.resultado.ReporteData.EventoReporte;
import py.simulador.resultado.ReporteData.RankingItem;
import py.simulador.resultado.ReporteData.TrimestreMetric;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Construye el reporte PDF "comercial" de una competencia. No accede a la base
 * de datos: recibe estructuras ya armadas por {@link PdfExportService} y se
 * encarga puramente del layout. Esto facilita los unit tests y permite reusar
 * el builder desde otros contextos (p. ej. preview en frontend).
 *
 * <p>Estructura del documento (orden fijo, todas las secciones opcionales
 * salvo la portada):
 * <ol>
 *   <li><b>Portada</b> — nombre, código, fechas, conteo de equipos y
 *       trimestres procesados.</li>
 *   <li><b>Resumen ejecutivo</b> (sección {@code ranking}) — ranking final
 *       resaltando podio top 3 con colores oro/plata/bronce.</li>
 *   <li><b>Eventos destacados</b> (sección {@code eventos}) — eventos
 *       disparados en la competencia con su trimestre y magnitud.</li>
 *   <li><b>Páginas por equipo</b> (sección {@code resultados}) — una página
 *       por equipo con métricas Q1..QN, badge de tipo (HUMANO/BOT) y mini
 *       chart de evolución de PIP + share.</li>
 *   <li><b>Apéndice opcional</b> (secciones {@code decisiones},
 *       {@code bitacora}) — tablas tabulares heredadas del reporte anterior.</li>
 * </ol>
 *
 * <p>Las secciones se controlan con el set pasado a
 * {@link #build(ReporteData, Set)}. Por defecto, {@link PdfExportService}
 * incluye {@code ranking}, {@code resultados} y {@code eventos}.
 */
public class ReportePdfBuilder {

    // --- Tipografía / paleta ---
    private static final Font COVER_TITLE_FONT = new Font(Font.HELVETICA, 28, Font.BOLD, new Color(20, 30, 50));
    private static final Font COVER_SUBTITLE_FONT = new Font(Font.HELVETICA, 18, Font.NORMAL, new Color(60, 70, 90));
    private static final Font COVER_LABEL_FONT = new Font(Font.HELVETICA, 11, Font.NORMAL, new Color(90, 90, 90));
    private static final Font COVER_VALUE_FONT = new Font(Font.HELVETICA, 13, Font.BOLD, new Color(30, 40, 60));
    private static final Font H1_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(20, 30, 50));
    private static final Font H2_FONT = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(20, 30, 50));
    private static final Font H3_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(40, 50, 70));
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(40, 40, 40));
    private static final Font CELL_FONT = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font CELL_BOLD_FONT = new Font(Font.HELVETICA, 9, Font.BOLD);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
    private static final Font BADGE_FONT = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
    private static final Font FOOTER_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(120, 120, 120));

    private static final Color HEADER_BG = new Color(44, 62, 80);
    private static final Color ALT_ROW_BG = new Color(245, 247, 250);
    private static final Color GOLD_BG = new Color(255, 215, 0);
    private static final Color SILVER_BG = new Color(192, 192, 192);
    private static final Color BRONZE_BG = new Color(205, 127, 50);
    private static final Color BOT_BADGE = new Color(123, 31, 162);   // morado
    private static final Color HUMAN_BADGE = new Color(33, 150, 243); // azul
    private static final Color CHART_BAR = new Color(33, 150, 243);
    private static final Color CHART_AXIS = new Color(150, 150, 150);
    private static final Color CHART_GRID = new Color(220, 220, 220);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final NumberFormat NUM_FMT = NumberFormat.getNumberInstance(new Locale("es", "PY"));

    /**
     * Genera los bytes del PDF a partir de los datos pre-cargados.
     */
    public byte[] build(ReporteData data, Set<String> sections) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 54, 54);

        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new FooterEvent());
            doc.open();

            // 1. Portada
            renderCover(doc, data);

            // 2. Resumen ejecutivo + ranking
            if (sections.contains("ranking")) {
                renderRankingFinal(doc, data);
            }

            // 3. Eventos destacados
            if (sections.contains("eventos") && !data.eventos().isEmpty()) {
                renderEventosDestacados(doc, data);
            }

            // 4. Páginas por equipo
            if (sections.contains("resultados")) {
                for (EquipoReporte eq : data.equipos()) {
                    renderEquipoPage(doc, data, eq);
                }
            }

            // 5. Apéndices
            if (sections.contains("decisiones")) {
                renderDecisionesAppendix(doc, data);
            }
            if (sections.contains("bitacora")) {
                renderBitacoraAppendix(doc, data);
            }

            doc.close();
        } catch (DocumentException e) {
            throw new IOException("Error generando PDF", e);
        }

        return out.toByteArray();
    }

    // ============================================================
    // Portada
    // ============================================================

    private void renderCover(Document doc, ReporteData data) throws DocumentException {
        // Espacio superior
        addBlankLines(doc, 4);

        Paragraph title = new Paragraph("Reporte Final de Competencia", COVER_TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);

        addBlankLines(doc, 1);

        Paragraph subtitle = new Paragraph(data.competencia().nombre(), COVER_SUBTITLE_FONT);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        doc.add(subtitle);

        Paragraph codigo = new Paragraph("Código: " + data.competencia().codigo(), COVER_LABEL_FONT);
        codigo.setAlignment(Element.ALIGN_CENTER);
        doc.add(codigo);

        addBlankLines(doc, 4);

        // Tabla de metadatos centrada
        PdfPTable meta = new PdfPTable(2);
        meta.setWidthPercentage(60);
        meta.setHorizontalAlignment(Element.ALIGN_CENTER);
        meta.setSpacingBefore(20);

        addMetaRow(meta, "Estado", data.competencia().estado());
        addMetaRow(meta, "Trimestres", data.competencia().numTrimestres() + " (procesados: "
                + data.trimestresProcesados().size() + ")");
        addMetaRow(meta, "Equipos", String.valueOf(data.equipos().size()));
        long bots = data.equipos().stream().filter(EquipoReporte::esBot).count();
        if (bots > 0) {
            addMetaRow(meta, "  · Bots", bots + " / " + (data.equipos().size() - bots) + " humanos");
        }
        if (data.competencia().inicioAt() != null) {
            addMetaRow(meta, "Inicio", data.competencia().inicioAt().format(DATE_FMT));
        }
        if (data.competencia().cierreAt() != null) {
            addMetaRow(meta, "Cierre", data.competencia().cierreAt().format(DATE_FMT));
        }
        if (data.ganador() != null) {
            addMetaRow(meta, "Ganador", data.ganador());
        }
        doc.add(meta);

        addBlankLines(doc, 8);

        Paragraph footer = new Paragraph("Generado por Simulador de Negocios", SMALL_FONT);
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);
    }

    private void addMetaRow(PdfPTable table, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, COVER_LABEL_FONT));
        l.setBorder(Rectangle.NO_BORDER);
        l.setPadding(6);
        l.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(l);

        PdfPCell v = new PdfPCell(new Phrase(value, COVER_VALUE_FONT));
        v.setBorder(Rectangle.NO_BORDER);
        v.setPadding(6);
        v.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(v);
    }

    // ============================================================
    // Ranking final
    // ============================================================

    private void renderRankingFinal(Document doc, ReporteData data) throws DocumentException {
        doc.newPage();
        doc.add(new Paragraph("Ranking Final", H1_FONT));

        if (data.rankingFinal().isEmpty()) {
            doc.add(new Paragraph("(Sin trimestres procesados)", BODY_FONT));
            return;
        }

        Paragraph caption = new Paragraph(
                "Posiciones acumuladas tras Q" + data.numUltimoTrimestreProcesado(), SMALL_FONT);
        caption.setSpacingAfter(12);
        doc.add(caption);

        PdfPTable table = new PdfPTable(new float[]{8, 30, 14, 18, 18, 12});
        table.setWidthPercentage(100);
        table.setSpacingBefore(8);
        addHeaderRow(table, "#", "Equipo", "PIP Acum.", "Utilidad Acum.", "Caja", "Share");

        int row = 0;
        for (RankingItem r : data.rankingFinal()) {
            Color bg = podioColor(r.posicion(), row);
            Font font = (r.posicion() <= 3) ? CELL_BOLD_FONT : CELL_FONT;
            String posLabel = posicionConMedalla(r.posicion());

            addStyledRow(table, bg, font,
                    posLabel,
                    r.nombre() + (r.esBot() ? "  [BOT]" : ""),
                    fmt(r.pipAcumulado()),
                    fmtGs(r.utilidadAcumulada()),
                    fmtGs(r.cajaActual()),
                    fmtPct(r.shareActual()));
            row++;
        }
        doc.add(table);
    }

    private Color podioColor(int posicion, int rowIdx) {
        return switch (posicion) {
            case 1 -> GOLD_BG;
            case 2 -> SILVER_BG;
            case 3 -> BRONZE_BG;
            default -> (rowIdx % 2 == 1) ? ALT_ROW_BG : Color.WHITE;
        };
    }

    private String posicionConMedalla(int p) {
        return switch (p) {
            case 1 -> "1°";
            case 2 -> "2°";
            case 3 -> "3°";
            default -> p + "°";
        };
    }

    // ============================================================
    // Eventos destacados
    // ============================================================

    private void renderEventosDestacados(Document doc, ReporteData data) throws DocumentException {
        doc.newPage();
        doc.add(new Paragraph("Eventos del Mercado", H1_FONT));
        Paragraph caption = new Paragraph(
                "Sucesos macroeconómicos y sectoriales que influyeron en la competencia", SMALL_FONT);
        caption.setSpacingAfter(12);
        doc.add(caption);

        PdfPTable table = new PdfPTable(new float[]{6, 30, 12, 12, 12, 28});
        table.setWidthPercentage(100);
        addHeaderRow(table, "Q", "Evento", "Severidad", "Magnitud", "Duración", "Origen");

        int row = 0;
        for (EventoReporte e : data.eventos()) {
            Color bg = (row++ % 2 == 1) ? ALT_ROW_BG : Color.WHITE;
            addStyledRow(table, bg, CELL_FONT,
                    "Q" + e.trimestreNumero(),
                    e.nombre(),
                    nullSafe(e.severidad()),
                    fmt(e.magnitud()),
                    e.duracion() + "Q",
                    e.origen());
        }
        doc.add(table);
    }

    // ============================================================
    // Página por equipo
    // ============================================================

    private void renderEquipoPage(Document doc, ReporteData data, EquipoReporte eq) throws DocumentException {
        doc.newPage();

        // Encabezado del equipo: nombre + badge de tipo
        PdfPTable header = new PdfPTable(new float[]{60, 20, 20});
        header.setWidthPercentage(100);

        PdfPCell name = new PdfPCell(new Phrase(eq.nombre(), H1_FONT));
        name.setBorder(Rectangle.NO_BORDER);
        name.setPadding(0);
        header.addCell(name);

        // Badge HUMANO/BOT
        PdfPCell badge = new PdfPCell(new Phrase(eq.esBot() ? "BOT" : "HUMANO", BADGE_FONT));
        badge.setBackgroundColor(eq.esBot() ? BOT_BADGE : HUMAN_BADGE);
        badge.setHorizontalAlignment(Element.ALIGN_CENTER);
        badge.setVerticalAlignment(Element.ALIGN_MIDDLE);
        badge.setPadding(6);
        badge.setBorder(Rectangle.NO_BORDER);
        header.addCell(badge);

        // Posición final
        String posTxt = eq.posicionFinal() != null
                ? "Posición final: " + posicionConMedalla(eq.posicionFinal())
                : "";
        PdfPCell pos = new PdfPCell(new Phrase(posTxt, H3_FONT));
        pos.setBorder(Rectangle.NO_BORDER);
        pos.setHorizontalAlignment(Element.ALIGN_RIGHT);
        pos.setVerticalAlignment(Element.ALIGN_MIDDLE);
        header.addCell(pos);

        doc.add(header);

        if (eq.esBot() && eq.dificultad() != null) {
            Paragraph botInfo = new Paragraph(
                    "Bot · dificultad " + eq.dificultad()
                            + (eq.personalidad() != null ? " · personalidad " + eq.personalidad() : ""),
                    SMALL_FONT);
            doc.add(botInfo);
        }

        addBlankLines(doc, 1);

        // Tabla de métricas por Q
        doc.add(new Paragraph("Métricas por Trimestre", H2_FONT));
        addBlankLines(doc, 1);

        PdfPTable metrics = new PdfPTable(new float[]{8, 12, 14, 18, 18, 14, 12});
        metrics.setWidthPercentage(100);
        addHeaderRow(metrics, "Q", "Posición", "PIP", "Ingresos", "Utilidad Neta", "Share", "Caja");

        int row = 0;
        for (TrimestreMetric m : eq.metricas()) {
            Color bg = (row++ % 2 == 1) ? ALT_ROW_BG : Color.WHITE;
            addStyledRow(metrics, bg, CELL_FONT,
                    "Q" + m.trimestreNumero(),
                    m.posicion() != null ? m.posicion() + "°" : "—",
                    fmt(m.pip()),
                    fmtGs(m.ingresos()),
                    fmtGs(m.utilidadNeta()),
                    fmtPct(m.share()),
                    fmtGs(m.caja()));
        }
        doc.add(metrics);

        addBlankLines(doc, 2);

        // Charts: PIP evolution + share evolution
        doc.add(new Paragraph("Evolución", H2_FONT));
        addBlankLines(doc, 1);

        renderChartPair(doc, eq);
    }

    /**
     * Dibuja dos mini-charts lado a lado: PIP por Q y share por Q. Usa
     * primitivas de PdfContentByte (líneas + rectángulos), sin librerías
     * externas.
     */
    private void renderChartPair(Document doc, EquipoReporte eq) throws DocumentException {
        // Una tabla con 2 celdas para colocar los charts lado a lado
        PdfPTable container = new PdfPTable(2);
        container.setWidthPercentage(100);

        container.addCell(buildChartCell("PIP por trimestre",
                eq.metricas().stream().map(m -> m.pip() == null ? 0d : m.pip().doubleValue()).toList(),
                eq.metricas().stream().map(m -> "Q" + m.trimestreNumero()).toList(),
                false));

        container.addCell(buildChartCell("Share por trimestre (%)",
                eq.metricas().stream().map(m -> m.share() == null
                        ? 0d : m.share().multiply(BigDecimal.valueOf(100)).doubleValue()).toList(),
                eq.metricas().stream().map(m -> "Q" + m.trimestreNumero()).toList(),
                true));

        doc.add(container);
    }

    private PdfPCell buildChartCell(String title, List<Double> values, List<String> labels, boolean isPercent) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(new Color(220, 220, 220));
        cell.setPadding(8);
        cell.setFixedHeight(160);

        Paragraph t = new Paragraph(title, H3_FONT);
        cell.addElement(t);

        // Usamos un Chunk con un CellRenderer custom: dibujamos el chart como
        // una imagen "drawn" via DrawInterface
        cell.setCellEvent(new BarChartEvent(values, labels, isPercent));
        return cell;
    }

    /**
     * CellEvent que dibuja un bar chart simple sobre la celda del PdfPTable.
     * El render se hace cuando OpenPDF compone la página.
     */
    private static class BarChartEvent implements com.lowagie.text.pdf.PdfPCellEvent {
        private static final com.lowagie.text.pdf.BaseFont CHART_FONT;
        static {
            try {
                CHART_FONT = com.lowagie.text.pdf.BaseFont.createFont();
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final List<Double> values;
        private final List<String> labels;
        private final boolean isPercent;

        BarChartEvent(List<Double> values, List<String> labels, boolean isPercent) {
            this.values = values;
            this.labels = labels;
            this.isPercent = isPercent;
        }

        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[PdfPTable.LINECANVAS];

            // Reservamos espacio para el título (parte superior, ~22pt)
            float titleSpace = 22f;
            // Y para las labels en el eje X (parte inferior, ~14pt)
            float labelSpace = 14f;
            float padding = 8f;

            float chartLeft = position.getLeft() + padding + 18f;   // espacio para eje Y
            float chartRight = position.getRight() - padding;
            float chartTop = position.getTop() - padding - titleSpace;
            float chartBottom = position.getBottom() + padding + labelSpace;

            float chartW = chartRight - chartLeft;
            float chartH = chartTop - chartBottom;

            if (chartW <= 0 || chartH <= 0 || values.isEmpty()) {
                return;
            }

            double maxVal = values.stream().mapToDouble(Double::doubleValue).max().orElse(1d);
            double minVal = values.stream().mapToDouble(Double::doubleValue).min().orElse(0d);
            // Aseguramos baseline en 0 si todos los valores son no negativos,
            // o incluimos el mínimo si hay negativos.
            double baseline = Math.min(0d, minVal);
            double range = Math.max(0.001d, maxVal - baseline);

            // --- Ejes ---
            cb.saveState();
            cb.setColorStroke(CHART_AXIS);
            cb.setLineWidth(0.5f);
            cb.moveTo(chartLeft, chartBottom);
            cb.lineTo(chartLeft, chartTop);
            cb.moveTo(chartLeft, chartBottom);
            cb.lineTo(chartRight, chartBottom);
            cb.stroke();

            // --- Grid horizontal (3 líneas) ---
            cb.setColorStroke(CHART_GRID);
            cb.setLineWidth(0.3f);
            for (int i = 1; i <= 3; i++) {
                float y = chartBottom + (chartH * i / 4f);
                cb.moveTo(chartLeft, y);
                cb.lineTo(chartRight, y);
                cb.stroke();
            }

            // --- Barras ---
            cb.setColorFill(CHART_BAR);
            int n = values.size();
            float gap = chartW / (n * 4f);
            float barW = (chartW - gap * (n + 1)) / n;
            if (barW < 4f) barW = 4f;

            float zeroY = chartBottom + (float) ((0 - baseline) / range * chartH);

            for (int i = 0; i < n; i++) {
                double v = values.get(i);
                float barH = (float) (Math.abs(v - 0) / range * chartH);
                float x = chartLeft + gap + i * (barW + gap);
                float y = (v >= 0) ? zeroY : zeroY - barH;
                cb.rectangle(x, y, barW, barH);
                cb.fill();

                // Label X centrada bajo cada barra
                cb.beginText();
                cb.setFontAndSize(CHART_FONT, 7f);
                cb.setColorFill(new Color(80, 80, 80));
                String lbl = labels.get(i);
                float textW = cb.getEffectiveStringWidth(lbl, false);
                cb.setTextMatrix(x + barW / 2f - textW / 2f, chartBottom - 10f);
                cb.showText(lbl);
                cb.endText();

                // Valor encima de la barra
                cb.beginText();
                cb.setFontAndSize(CHART_FONT, 7f);
                cb.setColorFill(new Color(60, 60, 60));
                String valTxt = formatChartValue(v, isPercent);
                float vTextW = cb.getEffectiveStringWidth(valTxt, false);
                cb.setTextMatrix(x + barW / 2f - vTextW / 2f, y + barH + 2f);
                cb.showText(valTxt);
                cb.endText();
            }

            cb.restoreState();
        }

        private String formatChartValue(double v, boolean isPercent) {
            if (isPercent) {
                return String.format(Locale.US, "%.1f", v);
            }
            if (Math.abs(v) >= 100) {
                return String.format(Locale.US, "%.0f", v);
            }
            return String.format(Locale.US, "%.2f", v);
        }
    }

    // ============================================================
    // Apéndices (formato simple, heredado)
    // ============================================================

    private void renderDecisionesAppendix(Document doc, ReporteData data) throws DocumentException {
        doc.newPage();
        doc.add(new Paragraph("Apéndice — Decisiones", H1_FONT));
        addBlankLines(doc, 1);

        if (data.decisiones().isEmpty()) {
            doc.add(new Paragraph("(Sin decisiones)", BODY_FONT));
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{6, 18, 12, 10, 12, 12, 10, 12, 12, 10});
        table.setWidthPercentage(100);
        addHeaderRow(table, "Q", "Equipo", "Precio", "Prod.", "Mktg.",
                "Inv.Cap.", "Contrat.", "I+D", "Préstamo", "Divid.");

        int row = 0;
        for (DecisionReporte d : data.decisiones()) {
            Color bg = (row++ % 2 == 1) ? ALT_ROW_BG : Color.WHITE;
            addStyledRow(table, bg, CELL_FONT,
                    "Q" + d.trimestreNumero(),
                    d.equipoNombre(),
                    fmtGs(d.precio()),
                    NUM_FMT.format(d.produccion()),
                    fmtGs(d.marketing()),
                    fmtGs(d.invCapacidad()),
                    String.valueOf(d.contrataciones()),
                    fmtGs(d.invId()),
                    fmtGs(d.prestamo()),
                    fmtGs(d.dividendos()));
        }
        doc.add(table);
    }

    private void renderBitacoraAppendix(Document doc, ReporteData data) throws DocumentException {
        doc.newPage();
        doc.add(new Paragraph("Apéndice — Bitácora de Auditoría", H1_FONT));
        addBlankLines(doc, 1);

        if (data.auditoria().isEmpty()) {
            doc.add(new Paragraph("(Sin registros de auditoría)", BODY_FONT));
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{18, 15, 67});
        table.setWidthPercentage(100);
        addHeaderRow(table, "Fecha", "Acción", "Descripción");

        int row = 0;
        for (AuditoriaReporte a : data.auditoria()) {
            Color bg = (row++ % 2 == 1) ? ALT_ROW_BG : Color.WHITE;
            addStyledRow(table, bg, CELL_FONT,
                    a.fecha() != null ? a.fecha().format(DATETIME_FMT) : "",
                    nullSafe(a.tipo()),
                    nullSafe(a.descripcion()));
        }
        doc.add(table);
    }

    // ============================================================
    // Helpers de tabla
    // ============================================================

    private void addHeaderRow(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, HEADER_FONT));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private void addStyledRow(PdfPTable table, Color bg, Font font, String... values) {
        for (String v : values) {
            PdfPCell cell = new PdfPCell(new Phrase(v != null ? v : "", font));
            cell.setBackgroundColor(bg);
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private void addBlankLines(Document doc, int n) throws DocumentException {
        for (int i = 0; i < n; i++) {
            doc.add(new Paragraph(" "));
        }
    }

    // ============================================================
    // Footer con número de página
    // ============================================================

    private static class FooterEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Phrase footer = new Phrase(
                    "Generado por Simulador · página " + writer.getPageNumber(),
                    FOOTER_FONT);
            com.lowagie.text.pdf.ColumnText.showTextAligned(
                    cb, Element.ALIGN_CENTER,
                    footer,
                    (document.right() + document.left()) / 2,
                    document.bottom() - 20,
                    0);
        }
    }

    // ============================================================
    // Format helpers
    // ============================================================

    private static String fmt(BigDecimal value) {
        return value != null ? value.setScale(2, RoundingMode.HALF_UP).toPlainString() : "0";
    }

    private static String fmtGs(long value) {
        return "Gs. " + NUM_FMT.format(value);
    }

    private static String fmtPct(BigDecimal value) {
        if (value == null) return "0%";
        return value.multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP) + "%";
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }
}

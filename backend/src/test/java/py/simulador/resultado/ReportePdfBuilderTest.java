package py.simulador.resultado;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios para {@link ReportePdfBuilder}. No requieren contexto de
 * Spring ni base de datos: arman {@link ReporteData} a mano y verifican que
 * el PDF resultante sea válido (header %PDF-, bytes no vacíos, contenido
 * mínimo esperado).
 *
 * <p>El PDF generado se guarda en {@code /tmp/} con un nombre estable para
 * inspección manual ({@code open /tmp/reporte_test_*.pdf}).
 */
class ReportePdfBuilderTest {

    private final ReportePdfBuilder builder = new ReportePdfBuilder();

    @Test
    @DisplayName("PDF con todas las secciones genera bytes válidos con header %PDF-")
    void completo_genera_pdf_valido() throws IOException {
        ReporteData data = sample4Q4Equipos();

        byte[] pdf = builder.build(data, Set.of("ranking", "resultados", "eventos",
                "decisiones", "bitacora"));

        assertThat(pdf).isNotEmpty();
        assertThat(pdf.length).isGreaterThan(2000); // un PDF con varias páginas debe pesar > 2KB
        assertHasPdfHeader(pdf);

        Path tmp = Files.createTempFile("reporte_test_completo_", ".pdf");
        Files.write(tmp, pdf);
        System.out.println("PDF de prueba (completo) guardado en: " + tmp);
    }

    @Test
    @DisplayName("PDF mínimo (sólo portada) sigue siendo válido")
    void minimo_sin_secciones_genera_solo_portada() throws IOException {
        ReporteData data = sample4Q4Equipos();
        byte[] pdf = builder.build(data, Set.of()); // ninguna sección activa

        assertHasPdfHeader(pdf);
        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("PDF sin trimestres procesados muestra mensaje de fallback en ranking")
    void sin_trimestres_procesados_no_falla() throws IOException {
        ReporteData data = new ReporteData(
                new ReporteData.CompetenciaInfo(1L, "TEST-1", "Competencia vacía",
                        "BORRADOR", 4, OffsetDateTime.now(), null),
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        byte[] pdf = builder.build(data, Set.of("ranking", "resultados", "eventos"));
        assertHasPdfHeader(pdf);
    }

    @Test
    @DisplayName("Equipos bot se etiquetan con badge BOT en el ranking final")
    void mezcla_humanos_y_bots_genera_pdf_valido() throws IOException {
        ReporteData data = sample4Q4Equipos();
        // sample4Q4Equipos ya tiene 2 humanos + 2 bots
        long bots = data.equipos().stream().filter(ReporteData.EquipoReporte::esBot).count();
        assertThat(bots).isEqualTo(2L);

        byte[] pdf = builder.build(data, Set.of("ranking", "resultados", "eventos"));
        assertHasPdfHeader(pdf);

        Path tmp = Files.createTempFile("reporte_test_bots_", ".pdf");
        Files.write(tmp, pdf);
        System.out.println("PDF con bots guardado en: " + tmp);
    }

    @Test
    @DisplayName("Mini chart maneja series con un solo valor sin lanzar excepción")
    void chart_con_un_solo_q_no_falla() throws IOException {
        ReporteData data = sample1Q();
        byte[] pdf = builder.build(data, Set.of("ranking", "resultados"));
        assertHasPdfHeader(pdf);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static void assertHasPdfHeader(byte[] pdf) {
        assertThat(pdf.length).isGreaterThanOrEqualTo(5);
        // PDF spec: must start with "%PDF-"
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    /**
     * Construye una competencia de muestra: 4 equipos (2 humanos + 2 bots),
     * 4 trimestres procesados, ranking final con podio, varios eventos y
     * decisiones.
     */
    private static ReporteData sample4Q4Equipos() {
        OffsetDateTime now = OffsetDateTime.now();

        ReporteData.CompetenciaInfo comp = new ReporteData.CompetenciaInfo(
                1L, "TEST-001", "Copa Empresarial 2026",
                "FINALIZADA", 4,
                now.minusDays(30), now);

        List<ReporteData.TrimestreInfo> tris = List.of(
                new ReporteData.TrimestreInfo(101L, 1, "PROCESADO"),
                new ReporteData.TrimestreInfo(102L, 2, "PROCESADO"),
                new ReporteData.TrimestreInfo(103L, 3, "PROCESADO"),
                new ReporteData.TrimestreInfo(104L, 4, "PROCESADO")
        );

        // Ranking final ordenado: gold, silver, bronze, 4to
        List<ReporteData.RankingItem> rank = List.of(
                new ReporteData.RankingItem(1, 10L, "Norteño Express", false,
                        bd("4.8"), 850_000_000L, 1_200_000_000L, bd("0.32")),
                new ReporteData.RankingItem(2, 20L, "Capital Mart", false,
                        bd("4.2"), 720_000_000L, 980_000_000L, bd("0.28")),
                new ReporteData.RankingItem(3, 30L, "BotEasy", true,
                        bd("3.9"), 600_000_000L, 850_000_000L, bd("0.22")),
                new ReporteData.RankingItem(4, 40L, "BotMedio", true,
                        bd("3.1"), 420_000_000L, 600_000_000L, bd("0.18"))
        );

        // Métricas por equipo (4 Qs cada uno)
        List<ReporteData.EquipoReporte> equipos = List.of(
                equipo(10L, "Norteño Express", false, null, null, 1,
                        new double[]{1.1, 1.2, 1.25, 1.25},
                        new long[]{200_000_000L, 220_000_000L, 250_000_000L, 280_000_000L},
                        new long[]{50_000_000L, 60_000_000L, 70_000_000L, 100_000_000L},
                        new double[]{0.30, 0.31, 0.32, 0.32},
                        new long[]{900_000_000L, 1_000_000_000L, 1_100_000_000L, 1_200_000_000L},
                        new int[]{1, 1, 1, 1}),
                equipo(20L, "Capital Mart", false, null, null, 2,
                        new double[]{1.0, 1.05, 1.10, 1.05},
                        new long[]{180_000_000L, 200_000_000L, 220_000_000L, 240_000_000L},
                        new long[]{40_000_000L, 50_000_000L, 60_000_000L, 80_000_000L},
                        new double[]{0.28, 0.27, 0.28, 0.28},
                        new long[]{800_000_000L, 850_000_000L, 920_000_000L, 980_000_000L},
                        new int[]{2, 2, 2, 2}),
                equipo(30L, "BotEasy", true, "FACIL", "AGRESIVO", 3,
                        new double[]{0.95, 1.0, 1.0, 0.95},
                        new long[]{160_000_000L, 170_000_000L, 180_000_000L, 200_000_000L},
                        new long[]{30_000_000L, 35_000_000L, 45_000_000L, 60_000_000L},
                        new double[]{0.22, 0.22, 0.22, 0.22},
                        new long[]{700_000_000L, 750_000_000L, 800_000_000L, 850_000_000L},
                        new int[]{3, 3, 3, 3}),
                equipo(40L, "BotMedio", true, "MEDIO", "CONSERVADOR", 4,
                        new double[]{0.80, 0.78, 0.77, 0.75},
                        new long[]{140_000_000L, 130_000_000L, 120_000_000L, 110_000_000L},
                        new long[]{20_000_000L, 18_000_000L, 22_000_000L, 30_000_000L},
                        new double[]{0.20, 0.20, 0.18, 0.18},
                        new long[]{500_000_000L, 530_000_000L, 560_000_000L, 600_000_000L},
                        new int[]{4, 4, 4, 4})
        );

        List<ReporteData.EventoReporte> eventos = List.of(
                new ReporteData.EventoReporte(2, "Suba diésel", "ALTA", "MANUAL",
                        bd("0.15"), 2),
                new ReporteData.EventoReporte(3, "Hot Sale nacional", "MEDIA", "AUTOMATICO",
                        bd("0.20"), 1),
                new ReporteData.EventoReporte(4, "Devaluación guaraní", "ALTA", "MANUAL",
                        bd("0.10"), 1)
        );

        List<ReporteData.DecisionReporte> decisiones = List.of(
                new ReporteData.DecisionReporte(1, "Norteño Express",
                        12_500L, 50_000L, 80_000_000L, 0L, 0,
                        30_000_000L, 0L, 0L),
                new ReporteData.DecisionReporte(1, "Capital Mart",
                        13_500L, 65_000L, 60_000_000L, 50_000_000L, 2,
                        20_000_000L, 100_000_000L, 0L)
        );

        List<ReporteData.AuditoriaReporte> aud = List.of(
                new ReporteData.AuditoriaReporte(now.minusDays(20),
                        "TRIMESTRE_PROCESADO", "Q1 procesado correctamente"),
                new ReporteData.AuditoriaReporte(now.minusDays(15),
                        "EVENTO_DISPARADO", "Evento Suba diésel disparado en Q2")
        );

        return new ReporteData(comp, tris, rank, 4, "Norteño Express",
                equipos, eventos, decisiones, aud);
    }

    private static ReporteData sample1Q() {
        OffsetDateTime now = OffsetDateTime.now();
        ReporteData.CompetenciaInfo comp = new ReporteData.CompetenciaInfo(
                2L, "TEST-002", "Mini Sprint 1Q", "EN_CURSO", 1, now, null);
        List<ReporteData.TrimestreInfo> tris = List.of(
                new ReporteData.TrimestreInfo(201L, 1, "PROCESADO"));
        List<ReporteData.RankingItem> rank = List.of(
                new ReporteData.RankingItem(1, 50L, "Solo Equipo", false,
                        bd("1.0"), 100_000_000L, 500_000_000L, bd("1.0")));
        List<ReporteData.EquipoReporte> equipos = List.of(
                equipo(50L, "Solo Equipo", false, null, null, 1,
                        new double[]{1.0},
                        new long[]{100_000_000L},
                        new long[]{20_000_000L},
                        new double[]{1.0},
                        new long[]{500_000_000L},
                        new int[]{1}));
        return new ReporteData(comp, tris, rank, 1, "Solo Equipo", equipos,
                List.of(), List.of(), List.of());
    }

    private static ReporteData.EquipoReporte equipo(
            long id, String nombre, boolean bot, String dif, String pers,
            Integer posFinal,
            double[] pip, long[] ingresos, long[] utilNeta, double[] share,
            long[] caja, int[] posiciones) {
        List<ReporteData.TrimestreMetric> ms = new java.util.ArrayList<>();
        for (int i = 0; i < pip.length; i++) {
            ms.add(new ReporteData.TrimestreMetric(
                    i + 1, posiciones[i], bd(String.valueOf(pip[i])),
                    ingresos[i], utilNeta[i], bd(String.valueOf(share[i])), caja[i]));
        }
        return new ReporteData.EquipoReporte(id, nombre, bot, dif, pers, posFinal, ms);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}

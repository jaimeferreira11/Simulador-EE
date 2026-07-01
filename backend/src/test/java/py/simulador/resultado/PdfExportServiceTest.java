package py.simulador.resultado;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import py.simulador.auditoria.AuditoriaEventoEntity;
import py.simulador.auditoria.AuditoriaEventoRepository;
import py.simulador.catalogo.EventoCatalogoEntity;
import py.simulador.catalogo.EventoCatalogoRepository;
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
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests del orquestador {@link PdfExportService}: arma una competencia mock
 * con repositorios falsos (Mockito) y verifica que:
 * <ul>
 *   <li>{@link PdfExportService#cargar(Long)} arma un {@link ReporteData}
 *       coherente (ranking ordenado, equipos enriquecidos con badge bot,
 *       métricas por Q completas).</li>
 *   <li>{@link PdfExportService#exportar(Long, java.util.Set)} produce un PDF
 *       válido (header %PDF-) usando ese mismo data.</li>
 * </ul>
 *
 * <p>No requiere base de datos.
 */
@ExtendWith(MockitoExtension.class)
class PdfExportServiceTest {

    @Mock CompetenciaRepository competenciaRepo;
    @Mock EquipoRepository equipoRepo;
    @Mock TrimestreRepository trimestreRepo;
    @Mock RankingTrimestreRepository rankingRepo;
    @Mock ResultadoCalculoRepository resultadoRepo;
    @Mock SnapshotEstadoRepository snapshotRepo;
    @Mock EventoCompetenciaRepository eventoRepo;
    @Mock EventoCatalogoRepository catalogoRepo;
    @Mock DecisionEquipoRepository decisionRepo;
    @Mock AuditoriaEventoRepository auditoriaRepo;

    private PdfExportService service;

    private static final Long COMP_ID = 99L;
    private static final Long TRI_1 = 991L;
    private static final Long TRI_2 = 992L;
    private static final Long EQ_HUMANO = 100L;
    private static final Long EQ_BOT = 200L;

    @BeforeEach
    void setUp() {
        service = new PdfExportService(
                competenciaRepo, equipoRepo, trimestreRepo, rankingRepo,
                resultadoRepo, snapshotRepo, eventoRepo, catalogoRepo,
                decisionRepo, auditoriaRepo);

        // --- Competencia ---
        CompetenciaEntity comp = new CompetenciaEntity();
        comp.setId(COMP_ID);
        comp.setCodigo("MOCK-1");
        comp.setNombre("Mock Cup");
        comp.setEstado("FINALIZADA");
        comp.setNumTrimestres((short) 2);
        comp.setInicioAt(OffsetDateTime.now().minusDays(10));
        comp.setCierreAt(OffsetDateTime.now());
        when(competenciaRepo.findById(COMP_ID)).thenReturn(Optional.of(comp));

        // --- Equipos: 1 humano + 1 bot ---
        EquipoEntity humano = new EquipoEntity();
        humano.setId(EQ_HUMANO);
        humano.setCompetenciaId(COMP_ID);
        humano.setNombreEmpresa("Equipo Humano");
        humano.setTipo("HUMANO");

        EquipoEntity bot = new EquipoEntity();
        bot.setId(EQ_BOT);
        bot.setCompetenciaId(COMP_ID);
        bot.setNombreEmpresa("Equipo Bot");
        bot.setTipo("BOT");
        bot.setDificultad("MEDIO");
        bot.setPersonalidad("AGRESIVO");
        when(equipoRepo.findByCompetenciaId(COMP_ID)).thenReturn(List.of(humano, bot));

        // --- Trimestres: 2, ambos PROCESADO ---
        TrimestreEntity t1 = trimestre(TRI_1, (short) 1);
        TrimestreEntity t2 = trimestre(TRI_2, (short) 2);
        when(trimestreRepo.findByCompetenciaId(COMP_ID)).thenReturn(List.of(t1, t2));

        // --- Resultados por trimestre ---
        when(resultadoRepo.findByTrimestreId(TRI_1)).thenReturn(List.of(
                resultado(EQ_HUMANO, TRI_1, "1.2", 100_000_000L, 20_000_000L, "0.55"),
                resultado(EQ_BOT,   TRI_1, "1.0",  80_000_000L, 12_000_000L, "0.45")
        ));
        when(resultadoRepo.findByTrimestreId(TRI_2)).thenReturn(List.of(
                resultado(EQ_HUMANO, TRI_2, "1.3", 120_000_000L, 25_000_000L, "0.58"),
                resultado(EQ_BOT,   TRI_2, "1.05", 90_000_000L, 14_000_000L, "0.42")
        ));

        // --- Rankings por trimestre ---
        when(rankingRepo.findByCompetenciaIdAndTrimestreId(COMP_ID, TRI_1)).thenReturn(List.of(
                ranking(1, EQ_HUMANO, "1.2", 20_000_000L, 200_000_000L, "0.55"),
                ranking(2, EQ_BOT,   "1.0", 12_000_000L, 150_000_000L, "0.45")
        ));
        when(rankingRepo.findByCompetenciaIdAndTrimestreId(COMP_ID, TRI_2)).thenReturn(List.of(
                ranking(1, EQ_HUMANO, "2.5", 45_000_000L, 220_000_000L, "0.58"),
                ranking(2, EQ_BOT,   "2.05", 26_000_000L, 165_000_000L, "0.42")
        ));

        // --- Snapshots CIERRE ---
        when(snapshotRepo.findByTrimestreIdAndMomento(TRI_1, "CIERRE")).thenReturn(List.of(
                snapshot(EQ_HUMANO, TRI_1, 200_000_000L),
                snapshot(EQ_BOT,   TRI_1, 150_000_000L)
        ));
        when(snapshotRepo.findByTrimestreIdAndMomento(TRI_2, "CIERRE")).thenReturn(List.of(
                snapshot(EQ_HUMANO, TRI_2, 220_000_000L),
                snapshot(EQ_BOT,   TRI_2, 165_000_000L)
        ));

        // --- Eventos ---
        EventoCompetenciaEntity ev = new EventoCompetenciaEntity();
        ev.setId(1L);
        ev.setCompetenciaId(COMP_ID);
        ev.setTrimestreId(TRI_2);
        ev.setEventoCatalogoId(7L);
        ev.setOrigen("MANUAL");
        ev.setMagnitudAplicada(new BigDecimal("0.10"));
        ev.setDuracionAplicada((short) 1);
        when(eventoRepo.findByCompetenciaId(COMP_ID)).thenReturn(List.of(ev));

        EventoCatalogoEntity cat = new EventoCatalogoEntity();
        cat.setId(7L);
        cat.setNombre("Suba diésel");
        cat.setSeveridad("ALTA");
        when(catalogoRepo.findById(7L)).thenReturn(Optional.of(cat));

        // --- Decisiones (vacías por defecto, evitan que la sección decisiones explote) ---
        when(decisionRepo.findByTrimestreId(any())).thenReturn(List.of());

        // --- Auditoria (vacía) ---
        when(auditoriaRepo.findByCompetenciaId(COMP_ID)).thenReturn(List.<AuditoriaEventoEntity>of());

        // findByCompetenciaId del ranking se usa también: lenient para no marcar unused stubs
        lenient().when(rankingRepo.findByCompetenciaId(COMP_ID)).thenReturn(List.of());
    }

    @Test
    @DisplayName("cargar() arma ReporteData con orden de equipos por posición final")
    void cargar_ordena_equipos_por_posicion_final() {
        ReporteData data = service.cargar(COMP_ID);

        assertThat(data.competencia().nombre()).isEqualTo("Mock Cup");
        assertThat(data.trimestresProcesados()).hasSize(2);
        assertThat(data.numUltimoTrimestreProcesado()).isEqualTo(2);
        assertThat(data.ganador()).isEqualTo("Equipo Humano");

        // Ranking final ordenado: humano 1°, bot 2°
        assertThat(data.rankingFinal()).hasSize(2);
        assertThat(data.rankingFinal().get(0).nombre()).isEqualTo("Equipo Humano");
        assertThat(data.rankingFinal().get(0).esBot()).isFalse();
        assertThat(data.rankingFinal().get(1).nombre()).isEqualTo("Equipo Bot");
        assertThat(data.rankingFinal().get(1).esBot()).isTrue();

        // Equipos ordenados por posición final
        assertThat(data.equipos()).hasSize(2);
        assertThat(data.equipos().get(0).nombre()).isEqualTo("Equipo Humano");
        assertThat(data.equipos().get(0).posicionFinal()).isEqualTo(1);
        assertThat(data.equipos().get(1).nombre()).isEqualTo("Equipo Bot");
        assertThat(data.equipos().get(1).esBot()).isTrue();
        assertThat(data.equipos().get(1).dificultad()).isEqualTo("MEDIO");
        assertThat(data.equipos().get(1).personalidad()).isEqualTo("AGRESIVO");

        // Cada equipo tiene métricas para los 2 Qs
        assertThat(data.equipos().get(0).metricas()).hasSize(2);
        assertThat(data.equipos().get(0).metricas().get(0).trimestreNumero()).isEqualTo(1);
        assertThat(data.equipos().get(0).metricas().get(0).ingresos()).isEqualTo(100_000_000L);
        assertThat(data.equipos().get(0).metricas().get(0).caja()).isEqualTo(200_000_000L);

        // Eventos
        assertThat(data.eventos()).hasSize(1);
        assertThat(data.eventos().get(0).nombre()).isEqualTo("Suba diésel");
        assertThat(data.eventos().get(0).severidad()).isEqualTo("ALTA");
        assertThat(data.eventos().get(0).trimestreNumero()).isEqualTo(2);
    }

    @Test
    @DisplayName("exportar() entrega bytes PDF válidos (header %PDF-) end-to-end")
    void exportar_produce_pdf_valido() throws IOException {
        byte[] pdf = service.exportar(COMP_ID, Set.of("ranking", "resultados", "eventos"));

        assertThat(pdf).isNotEmpty();
        assertThat(pdf.length).isGreaterThan(2000);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        Path tmp = Files.createTempFile("reporte_service_e2e_", ".pdf");
        Files.write(tmp, pdf);
        System.out.println("PDF service E2E guardado en: " + tmp);
    }

    @Test
    @DisplayName("exportar(id) usa secciones default (ranking + resultados + eventos)")
    void exportar_default_sections_funciona() throws IOException {
        byte[] pdf = service.exportar(COMP_ID);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static TrimestreEntity trimestre(Long id, short numero) {
        TrimestreEntity t = new TrimestreEntity();
        t.setId(id);
        t.setNumero(numero);
        t.setEstado("PROCESADO");
        return t;
    }

    private static ResultadoCalculoEntity resultado(Long equipoId, Long triId,
                                                     String pip, long ingresos,
                                                     long utilNeta, String share) {
        ResultadoCalculoEntity r = new ResultadoCalculoEntity();
        r.setEquipoId(equipoId);
        r.setTrimestreId(triId);
        r.setPipTrimestre(new BigDecimal(pip));
        r.setIngresos(ingresos);
        r.setUtilidadNeta(utilNeta);
        r.setShare(new BigDecimal(share));
        return r;
    }

    private static RankingTrimestreEntity ranking(int pos, Long equipoId,
                                                   String pipAcum, long utilAcum,
                                                   long caja, String share) {
        RankingTrimestreEntity r = new RankingTrimestreEntity();
        r.setPosicion((short) pos);
        r.setEquipoId(equipoId);
        r.setPipAcumulado(new BigDecimal(pipAcum));
        r.setUtilidadAcumulada(utilAcum);
        r.setCajaActual(caja);
        r.setShareActual(new BigDecimal(share));
        return r;
    }

    private static SnapshotEstadoEntity snapshot(Long equipoId, Long triId, long caja) {
        SnapshotEstadoEntity s = new SnapshotEstadoEntity();
        s.setEquipoId(equipoId);
        s.setTrimestreId(triId);
        s.setMomento("CIERRE");
        s.setCaja(caja);
        return s;
    }
}

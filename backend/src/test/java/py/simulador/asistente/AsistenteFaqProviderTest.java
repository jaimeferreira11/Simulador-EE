package py.simulador.asistente;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import py.simulador.asistente.dto.AsistenteContexto;
import py.simulador.asistente.dto.OrigenRespuesta;
import py.simulador.asistente.dto.RespuestaAsistente;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsistenteFaqProviderTest {

    @Mock AsistenteFaqRepository faqRepo;

    private AsistenteFaqEntity faq(String pregunta, String respuesta, String seccion, String... kws) {
        AsistenteFaqEntity f = new AsistenteFaqEntity();
        f.setPregunta(pregunta);
        f.setRespuesta(respuesta);
        f.setSeccionManual(seccion);
        f.setKeywords(kws);
        f.setActiva(true);
        return f;
    }

    @Test
    void devuelveLaFaqConMasPalabrasClaveCoincidentes() {
        when(faqRepo.findByActivaTrueOrderByOrdenAsc()).thenReturn(List.of(
                faq("¿Cómo se ordena el ranking?", "Por utilidad acumulada.", "Resultados financieros",
                        "ranking", "ordena", "criterio"),
                faq("¿Qué es el PIP?", "Es un índice de gestión.", "Resultados financieros", "pip", "indice")
        ));
        AsistenteFaqProvider provider = new AsistenteFaqProvider(faqRepo);

        RespuestaAsistente r = provider.responder(
                new AsistenteContexto("como se ordena el ranking?", 1L, null));

        assertThat(r.origen()).isEqualTo(OrigenRespuesta.FAQ);
        assertThat(r.texto()).isEqualTo("Por utilidad acumulada.");
        assertThat(r.fuentes()).extracting("anclaManual").containsExactly("Resultados financieros");
    }

    @Test
    void esInsensibleAAcentosYMayusculas() {
        when(faqRepo.findByActivaTrueOrderByOrdenAsc()).thenReturn(List.of(
                faq("¿Qué es la producción?", "Las unidades a fabricar.", "Decisiones y mercado",
                        "produccion", "producir")
        ));
        AsistenteFaqProvider provider = new AsistenteFaqProvider(faqRepo);

        RespuestaAsistente r = provider.responder(
                new AsistenteContexto("Cuánta PRODUCCIÓN conviene?", 1L, null));

        assertThat(r.origen()).isEqualTo(OrigenRespuesta.FAQ);
    }

    @Test
    void sinCoincidenciasDevuelveFallbackConSugerencias() {
        when(faqRepo.findByActivaTrueOrderByOrdenAsc()).thenReturn(List.of(
                faq("¿Qué es el PIP?", "Es un índice de gestión.", "Resultados financieros", "pip")
        ));
        AsistenteFaqProvider provider = new AsistenteFaqProvider(faqRepo);

        RespuestaAsistente r = provider.responder(
                new AsistenteContexto("xyz no existe nada", 1L, null));

        assertThat(r.origen()).isEqualTo(OrigenRespuesta.FALLBACK);
        assertThat(r.relacionadas()).contains("¿Qué es el PIP?");
        assertThat(r.fuentes()).isNotEmpty();
    }
}

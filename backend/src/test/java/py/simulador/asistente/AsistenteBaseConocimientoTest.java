package py.simulador.asistente;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsistenteBaseConocimientoTest {

    @Mock AsistenteFaqRepository faqRepo;

    private AsistenteFaqEntity faq(String pregunta, String respuesta) {
        AsistenteFaqEntity f = new AsistenteFaqEntity();
        f.setPregunta(pregunta);
        f.setRespuesta(respuesta);
        return f;
    }

    @Test
    void fragmentos_troceaElManualPorSeccionYAgregaLasFaq() {
        when(faqRepo.findByActivaTrueOrderByOrdenAsc())
                .thenReturn(java.util.List.of(faq("¿Qué es el PIP?", "Un índice de gestión.")));
        AsistenteBaseConocimiento base = new AsistenteBaseConocimiento(faqRepo);

        java.util.List<Fragmento> frags = base.fragmentos();

        // 15 secciones del manual + 1 FAQ
        assertThat(frags).hasSize(16);
        assertThat(frags).extracting(Fragmento::titulo)
                .contains("Glosario de términos",
                          "Paso 4 — Cargar decisiones (corazón del juego)",
                          "Consejos finales para jugar bien",
                          "¿Qué es el PIP?");
        Fragmento glosario = frags.stream()
                .filter(f -> f.titulo().equals("Glosario de términos")).findFirst().orElseThrow();
        assertThat(glosario.texto()).contains("Moderador");  // contenido real de la sección
    }

    @Test
    void fragmentos_seCachea() {
        when(faqRepo.findByActivaTrueOrderByOrdenAsc())
                .thenReturn(java.util.List.of(faq("P", "R")));
        AsistenteBaseConocimiento base = new AsistenteBaseConocimiento(faqRepo);
        base.fragmentos();
        base.fragmentos();
        org.mockito.Mockito.verify(faqRepo, org.mockito.Mockito.times(1))
                .findByActivaTrueOrderByOrdenAsc();
    }
}

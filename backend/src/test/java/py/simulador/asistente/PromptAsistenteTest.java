package py.simulador.asistente;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PromptAsistenteTest {

    @Test
    void construir_incluyeReglasDocumentacionYPregunta() {
        String p = PromptAsistente.construir("DOC-DE-PRUEBA", "¿cómo cargo decisiones?");

        assertThat(p).contains("NUNCA sugieras precios");
        assertThat(p).contains("DOC-DE-PRUEBA");
        assertThat(p).contains("¿cómo cargo decisiones?");
    }

    @Test
    void construir_incluyeLaReglaSobreDatosDelJugador() {
        String p = PromptAsistente.construir("DOC", "hola");
        assertThat(p).contains("ESTADO ACTUAL DE TU EMPRESA");
        assertThat(p).contains("nunca sugieras");
    }
}

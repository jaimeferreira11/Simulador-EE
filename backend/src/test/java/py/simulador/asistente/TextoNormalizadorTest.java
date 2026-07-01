package py.simulador.asistente;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TextoNormalizadorTest {

    @Test
    void normaliza_minusculasYSinAcentos() {
        assertThat(TextoNormalizador.normalizar("Producción Ström ")).isEqualTo("produccion strom");
    }

    @Test
    void normaliza_nullComoVacio() {
        assertThat(TextoNormalizador.normalizar(null)).isEqualTo("");
    }
}

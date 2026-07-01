package py.simulador.asistente;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GuardrailFilterTest {

    private final GuardrailFilter filtro = new GuardrailFilter();

    @Test
    void respuestaPrescriptiva_agregaLaNota() {
        String r = filtro.revisar("Te conviene poner el precio en Gs 40.000 este trimestre.");
        assertThat(r).contains(GuardrailFilter.NOTA);
    }

    @Test
    void respuestaInformativa_quedaIgual() {
        String original = "El precio tiene un peso de 40% en la competitividad del mercado.";
        assertThat(filtro.revisar(original)).isEqualTo(original);
    }

    @Test
    void montoInformativoSinRecomendacion_quedaIgual() {
        String original = "El precio promedio del mercado es de Gs 42.000.";
        assertThat(filtro.revisar(original)).isEqualTo(original);
    }

    @Test
    void palabraQueContienePon_conMonto_noEsPrescriptiva_quedaIgual() {
        String original = "La componente de precio del mercado ronda los Gs 42.000.";
        assertThat(filtro.revisar(original)).isEqualTo(original);
    }
}

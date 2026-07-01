package py.simulador.llm;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class TemplateProviderTest {

    private final TemplateProvider provider = new TemplateProvider();

    @Test
    void generarNarrativa_returnsDescripcion() {
        var ctx = new EventoContext(
            "Suba del diesel", "El precio del diesel subio 15%",
            "COSTO_LOGISTICO", new BigDecimal("0.15"), (short) 2,
            "ALTA", "Retail", 3
        );
        String result = provider.generarNarrativa(ctx);
        assertThat(result).isEqualTo("El precio del diesel subio 15%");
    }

    @Test
    void generarCoaching_returnsStructuredText() {
        var ctx = new CoachingContext(
            "Equipo Alpha", 2,
            500_000_000L, 350_000_000L, 80_000_000L,
            new BigDecimal("0.28"), 2, 4,
            15000L, 50_000_000L, 30_000_000L,
            new BigDecimal("72.5")
        );
        String result = provider.generarCoaching(ctx);
        assertThat(result).contains("Equipo Alpha");
        assertThat(result).contains("Q2");
    }
}

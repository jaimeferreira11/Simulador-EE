package py.simulador.llm;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class NarrativaServiceTest {

    private final TemplateProvider provider = new TemplateProvider();
    private final NarrativaService service = new NarrativaService(provider);

    @Test
    void generarNarrativa_delegatesToProvider() {
        var ctx = new EventoContext(
            "Hot Sale", "Evento de descuentos masivos impulsa demanda",
            "DEMANDA_TOTAL", new BigDecimal("0.10"), (short) 1,
            "MEDIA", "Retail", 3
        );
        String result = service.generarNarrativa(ctx);
        assertThat(result).isEqualTo("Evento de descuentos masivos impulsa demanda");
    }

    @Test
    void generarCoaching_delegatesToProvider() {
        var ctx = new CoachingContext(
            "Los Cracks", 1,
            400_000_000L, 300_000_000L, 50_000_000L,
            new BigDecimal("0.25"), 1, 4,
            12000L, 40_000_000L, 20_000_000L,
            new BigDecimal("68.0")
        );
        String result = service.generarCoaching(ctx);
        assertThat(result).contains("Los Cracks");
        assertThat(result).contains("Q1");
    }
}

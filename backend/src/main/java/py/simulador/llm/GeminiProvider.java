package py.simulador.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "simulador.llm.provider", havingValue = "gemini")
public class GeminiProvider implements LlmProvider {
    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);

    private final RestClient client;
    private final String model;
    private final String narrativaPrompt;
    private final String coachingPrompt;

    public GeminiProvider(LlmProperties props) {
        this.model = props.model() != null ? props.model() : "gemini-2.0-flash";
        this.client = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("x-goog-api-key", props.apiKey())
                .build();
        this.narrativaPrompt = loadPrompt("prompts/narrativa.txt");
        this.coachingPrompt = loadPrompt("prompts/coaching.txt");
    }

    @Override
    public String generarNarrativa(EventoContext ctx) {
        String prompt = narrativaPrompt
                .replace("{rubro_nombre}", ctx.rubroNombre())
                .replace("{trimestre_numero}", String.valueOf(ctx.trimestreNumero()))
                .replace("{evento_nombre}", ctx.eventoNombre())
                .replace("{evento_descripcion}", ctx.eventoDescripcion())
                .replace("{tipo_efecto}", ctx.tipoEfecto())
                .replace("{magnitud}", ctx.magnitud().toPlainString())
                .replace("{duracion}", String.valueOf(ctx.duracion()))
                .replace("{severidad}", ctx.severidad());
        return call(prompt);
    }

    @Override
    public String generarCoaching(CoachingContext ctx) {
        String prompt = coachingPrompt
                .replace("{equipo_nombre}", ctx.equipoNombre())
                .replace("{trimestre_numero}", String.valueOf(ctx.trimestreNumero()))
                .replace("{ingresos}", String.format("%,d", ctx.ingresos()))
                .replace("{costos_operativos}", String.format("%,d", ctx.costosOperativos()))
                .replace("{utilidad_neta}", String.format("%,d", ctx.utilidadNeta()))
                .replace("{market_share}", ctx.marketShare().multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).toPlainString())
                .replace("{posicion}", String.valueOf(ctx.posicion()))
                .replace("{total_equipos}", String.valueOf(ctx.totalEquipos()))
                .replace("{precio_unitario}", String.format("%,d", ctx.precioUnitario()))
                .replace("{inversion_marketing}", String.format("%,d", ctx.inversionMarketing()))
                .replace("{inversion_calidad}", String.format("%,d", ctx.inversionCalidad()))
                .replace("{pip}", ctx.pip().toPlainString());
        return call(prompt);
    }

    @Override
    public LlmCompletion completarPrompt(String prompt) {
        return callRich(prompt, 800, 0.4);
    }

    @SuppressWarnings("unchecked")
    private String call(String prompt) {
        return callRich(prompt, 400, 0.7).content();
    }

    @SuppressWarnings("unchecked")
    private LlmCompletion callRich(String prompt, int maxTokens, double temperature) {
        var body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "maxOutputTokens", maxTokens,
                        "temperature", temperature,
                        // Disable Gemini 2.5-flash "thinking" budget. Thinking tokens (~1100-1300) were
                        // consuming the maxOutputTokens cap before any visible text was emitted, causing
                        // finishReason=MAX_TOKENS and 1-sentence stubs in coaching/narrativa output.
                        // Setting thinkingBudget=0 yields full visible output and ~3-5x cost/latency reduction.
                        "thinkingConfig", Map.of("thinkingBudget", 0)
                )
        );
        var response = client.post()
                .uri("/models/{model}:generateContent", model)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        var candidates = (List<Map<String, Object>>) response.get("candidates");
        var content = (Map<String, Object>) candidates.get(0).get("content");
        var parts = (List<Map<String, Object>>) content.get("parts");
        String text = (String) parts.get(0).get("text");

        Integer promptTokens = null, completionTokens = null;
        var usage = (Map<String, Object>) response.get("usageMetadata");
        if (usage != null) {
            Object pt = usage.get("promptTokenCount");
            Object ct = usage.get("candidatesTokenCount");
            if (pt instanceof Number n) promptTokens = n.intValue();
            if (ct instanceof Number n) completionTokens = n.intValue();
        }
        return new LlmCompletion(text, promptTokens, completionTokens);
    }

    private static String loadPrompt(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load prompt: " + path, e);
        }
    }
}

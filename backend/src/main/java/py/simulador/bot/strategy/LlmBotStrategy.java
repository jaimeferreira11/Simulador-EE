package py.simulador.bot.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import py.simulador.bot.BotContext;
import py.simulador.bot.BotDecisionDTO;
import py.simulador.bot.BotStrategy;
import py.simulador.bot.model.Difficulty;
import py.simulador.bot.model.Personality;
import py.simulador.llm.LlmCompletion;
import py.simulador.llm.LlmProvider;

/**
 * Estrategia de bot para nivel EXPERTO (Fase 2).
 *
 * <p>Arma un prompt contextual ({@link LlmPromptBuilder}), pide una decisión al
 * {@link LlmProvider}, parsea el JSON devuelto a {@link BotDecisionDTO}.
 *
 * <p><b>Fallback:</b> ante cualquier excepción (timeout LLM, JSON inválido,
 * sanity-check fallido), delega a una {@link HeuristicStrategy} configurada
 * como DIFICIL + misma personalidad. El método {@link #lastResult()} permite
 * al orquestador saber si se ejecutó vía LLM o vía fallback (para escribir
 * la fila correspondiente en {@code bot_decision_log}).
 *
 * <p>No es thread-safe: cada bot debe usar su propia instancia. La factory ya
 * construye una instancia nueva por llamada.
 */
public class LlmBotStrategy implements BotStrategy {

    private static final Logger log = LoggerFactory.getLogger(LlmBotStrategy.class);

    private final LlmProvider provider;
    private final Personality personality;
    private final ObjectMapper objectMapper;
    private final HeuristicStrategy fallback;

    /** Resultado de la última invocación, para auditoría desde el orquestador. */
    private LastResult lastResult;

    public LlmBotStrategy(LlmProvider provider,
                          Personality personality,
                          ObjectMapper objectMapper) {
        this.provider = provider;
        this.personality = personality;
        this.objectMapper = objectMapper;
        this.fallback = new HeuristicStrategy(Difficulty.DIFICIL, personality);
    }

    @Override
    public BotDecisionDTO generate(BotContext ctx) {
        String prompt = LlmPromptBuilder.build(ctx);
        LlmCompletion completion = null;
        try {
            completion = provider.completarPrompt(prompt);
            if (completion == null || completion.content() == null) {
                throw new IllegalStateException("LLM returned null completion");
            }
            BotDecisionDTO parsed = parseAndValidate(completion.content(), ctx);
            this.lastResult = new LastResult(
                Outcome.LLM, completion.promptTokens(), completion.completionTokens(), null);
            return parsed;
        } catch (Exception e) {
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("LlmBotStrategy fallback (personality={}): {}", personality, reason);
            BotDecisionDTO fb = fallback.generate(ctx);
            this.lastResult = new LastResult(
                Outcome.LLM_FALLBACK,
                completion != null ? completion.promptTokens() : null,
                completion != null ? completion.completionTokens() : null,
                truncate(reason, 500));
            return fb;
        }
    }

    /**
     * Devuelve la metadata de la última generación. Pensado para que
     * {@code BotDecisionService} pueda registrarla en {@code bot_decision_log}.
     */
    public LastResult lastResult() {
        return lastResult;
    }

    private BotDecisionDTO parseAndValidate(String json, BotContext ctx) throws Exception {
        // Strip eventual markdown code fences si el modelo se zarpó
        String cleaned = stripCodeFences(json).trim();
        JsonNode node = objectMapper.readTree(cleaned);

        long precio       = requirePositiveLong(node, "precio_venta");
        long produccion   = requireNonNegativeLong(node, "produccion_planificada");
        long marketing    = requireNonNegativeLong(node, "inversion_marketing");
        long rd           = requireNonNegativeLong(node, "inversion_id");
        int  empleados    = (int) requirePositiveLong(node, "cantidad_empleados");
        long salario      = requireNonNegativeLong(node, "salario_promedio");
        long prestamo     = requireNonNegativeLong(node, "prestamo_solicitado");
        long inversionFin = requireNonNegativeLong(node, "inversion_financiera");

        return new BotDecisionDTO(
            precio, produccion, marketing, rd,
            empleados, salario, prestamo, inversionFin,
            String.format("EXPERTO/%s via LLM (eq=%s tri=%s)",
                personality, ctx.equipoId(), ctx.trimestreId())
        );
    }

    private static long requirePositiveLong(JsonNode node, String field) {
        long v = requireNonNegativeLong(node, field);
        if (v <= 0) {
            throw new IllegalArgumentException("Field " + field + " must be > 0, got " + v);
        }
        return v;
    }

    private static long requireNonNegativeLong(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        if (!v.canConvertToLong()) {
            throw new IllegalArgumentException(
                "Field " + field + " is not an integer: " + v.toString());
        }
        long out = v.asLong();
        if (out < 0) {
            throw new IllegalArgumentException("Field " + field + " must be >= 0, got " + out);
        }
        return out;
    }

    private static String stripCodeFences(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) t = t.substring(firstNl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * Resultado de la última invocación. Inmutable.
     *
     * @param outcome           {@link Outcome#LLM} si parseó OK; {@link Outcome#LLM_FALLBACK} si cayó al heurístico
     * @param promptTokens      tokens consumidos en el prompt (si el provider los expuso)
     * @param completionTokens  tokens consumidos en la respuesta (si el provider los expuso)
     * @param fallbackReason    motivo textual del fallback (null cuando outcome=LLM)
     */
    public record LastResult(
        Outcome outcome,
        Integer promptTokens,
        Integer completionTokens,
        String fallbackReason
    ) {}

    public enum Outcome {
        /** El LLM respondió y el JSON fue válido — la decisión proviene del modelo. */
        LLM,
        /** El LLM falló (excepción, JSON inválido, sanity-check) — la decisión proviene del heurístico DIFICIL. */
        LLM_FALLBACK
    }
}

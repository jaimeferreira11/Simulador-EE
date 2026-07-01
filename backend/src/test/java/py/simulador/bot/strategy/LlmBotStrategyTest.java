package py.simulador.bot.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import py.simulador.bot.BotContext;
import py.simulador.bot.BotDecisionDTO;
import py.simulador.bot.model.Difficulty;
import py.simulador.bot.model.Personality;
import py.simulador.llm.LlmCompletion;
import py.simulador.llm.LlmProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmBotStrategyTest {

    @Mock LlmProvider llmProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private LlmBotStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new LlmBotStrategy(llmProvider, Personality.PREMIUM, objectMapper);
    }

    private BotContext sampleContext() {
        return new BotContext(
            42L, 7L,
            Difficulty.EXPERTO, Personality.PREMIUM,
            10_000_000L,                  // caja
            500L,                         // inventario
            0.65,                         // brand equity
            5_000_000L,                   // I+D acumulado
            12_000L,                      // costo unitario
            BotContext.CAPACIDAD_DESCONOCIDA, // capacidad
            80_000_000L,                  // ingreso anterior
            10_000_000L,                  // ganancia anterior
            2,                            // posicion anterior
            4,                            // total equipos
            Map.of("salario_minimo_q1", 2_700_000L,
                   "demanda_base_trim", 4000L),
            List.of(new BotContext.EventoActivo(
                "DIESEL_UP", "Suba del diesel", Map.of("costo_unitario_delta", 0.10))),
            List.of(
                new BotContext.Competidor(101L, "Equipo Rojo", "HUMANO", 1),
                new BotContext.Competidor(102L, "Bot Azul",    "BOT",    3)
            )
        );
    }

    @Test
    void happy_path_parses_llm_json_response() {
        String json = """
            {
              "precio_venta": 15000,
              "produccion_planificada": 12000,
              "inversion_marketing": 50000000,
              "inversion_id": 30000000,
              "cantidad_empleados": 12,
              "salario_promedio": 3000000,
              "prestamo_solicitado": 0,
              "inversion_financiera": 0
            }
            """;
        when(llmProvider.completarPrompt(anyString()))
            .thenReturn(new LlmCompletion(json, 350, 80));

        BotDecisionDTO decision = strategy.generate(sampleContext());

        assertThat(decision.precioUnitario()).isEqualTo(15_000L);
        assertThat(decision.produccionUnidades()).isEqualTo(12_000L);
        assertThat(decision.inversionMarketing()).isEqualTo(50_000_000L);
        assertThat(decision.inversionRd()).isEqualTo(30_000_000L);
        assertThat(decision.cantidadEmpleados()).isEqualTo(12);
        assertThat(decision.salarioPromedio()).isEqualTo(3_000_000L);
        assertThat(decision.prestamoSolicitado()).isZero();
        assertThat(decision.inversionFinanciera()).isZero();

        // Audit metadata
        assertThat(strategy.lastResult()).isNotNull();
        assertThat(strategy.lastResult().outcome()).isEqualTo(LlmBotStrategy.Outcome.LLM);
        assertThat(strategy.lastResult().promptTokens()).isEqualTo(350);
        assertThat(strategy.lastResult().completionTokens()).isEqualTo(80);
        assertThat(strategy.lastResult().fallbackReason()).isNull();
    }

    @Test
    void invalid_json_falls_back_to_heuristic_dificil() {
        // LLM returns garbage that fails JSON parsing
        when(llmProvider.completarPrompt(anyString()))
            .thenReturn(LlmCompletion.of("this is not valid json {{{"));

        BotDecisionDTO decision = strategy.generate(sampleContext());

        // Decision must come from HeuristicStrategy(DIFICIL, PREMIUM) — non-null,
        // positive precio, sensible bounds
        assertThat(decision).isNotNull();
        assertThat(decision.precioUnitario()).isPositive();
        assertThat(decision.produccionUnidades()).isNotNegative();

        // Audit metadata signals fallback
        assertThat(strategy.lastResult().outcome())
            .isEqualTo(LlmBotStrategy.Outcome.LLM_FALLBACK);
        assertThat(strategy.lastResult().fallbackReason()).isNotBlank();
    }

    @Test
    void llm_exception_falls_back_to_heuristic_dificil() {
        // LLM call itself throws (timeout, network error, etc.)
        when(llmProvider.completarPrompt(anyString()))
            .thenThrow(new RuntimeException("LLM timeout after 30s"));

        BotDecisionDTO decision = strategy.generate(sampleContext());

        assertThat(decision).isNotNull();
        assertThat(decision.precioUnitario()).isPositive();
        assertThat(strategy.lastResult().outcome())
            .isEqualTo(LlmBotStrategy.Outcome.LLM_FALLBACK);
        assertThat(strategy.lastResult().fallbackReason())
            .contains("LLM timeout");
    }

    @Test
    void prompt_includes_personality_and_difficulty() {
        // Capture the prompt to assert structure
        when(llmProvider.completarPrompt(anyString()))
            .thenAnswer(inv -> {
                String prompt = inv.getArgument(0);
                assertThat(prompt).contains("PERSONALIDAD: PREMIUM");
                assertThat(prompt).contains("NIVEL: EXPERTO");
                assertThat(prompt).contains("precio_venta");
                assertThat(prompt).contains("produccion_planificada");
                return new LlmCompletion(
                    "{\"precio_venta\":15000,\"produccion_planificada\":1000," +
                    "\"inversion_marketing\":100,\"inversion_id\":100," +
                    "\"cantidad_empleados\":5,\"salario_promedio\":2700000," +
                    "\"prestamo_solicitado\":0,\"inversion_financiera\":0}",
                    100, 50);
            });

        strategy.generate(sampleContext());
        verify(llmProvider, times(1)).completarPrompt(anyString());
    }

    @Test
    void prompt_includes_market_context_and_active_events() {
        when(llmProvider.completarPrompt(anyString()))
            .thenAnswer(inv -> {
                String prompt = inv.getArgument(0);
                // Macro context
                assertThat(prompt).contains("CONTEXTO MACRO");
                assertThat(prompt).contains("salario_minimo_q1");
                assertThat(prompt).contains("demanda_base_trim");
                // Active events
                assertThat(prompt).contains("EVENTOS ACTIVOS");
                assertThat(prompt).contains("DIESEL_UP");
                assertThat(prompt).contains("Suba del diesel");
                // Competitors
                assertThat(prompt).contains("COMPETIDORES");
                assertThat(prompt).contains("Equipo Rojo");
                assertThat(prompt).contains("Bot Azul");
                return new LlmCompletion(
                    "{\"precio_venta\":15000,\"produccion_planificada\":1000," +
                    "\"inversion_marketing\":100,\"inversion_id\":100," +
                    "\"cantidad_empleados\":5,\"salario_promedio\":2700000," +
                    "\"prestamo_solicitado\":0,\"inversion_financiera\":0}",
                    null, null);
            });

        strategy.generate(sampleContext());
    }

    @Test
    void deterministic_on_same_context_with_mocked_llm() {
        // Same prompt → same LLM response → same decision (the LLM mock is stable)
        String json = "{\"precio_venta\":18000,\"produccion_planificada\":2000," +
            "\"inversion_marketing\":1000000,\"inversion_id\":500000," +
            "\"cantidad_empleados\":4,\"salario_promedio\":2700000," +
            "\"prestamo_solicitado\":0,\"inversion_financiera\":0}";
        when(llmProvider.completarPrompt(anyString()))
            .thenReturn(LlmCompletion.of(json));

        var ctx = sampleContext();
        var d1 = strategy.generate(ctx);
        var d2 = strategy.generate(ctx);
        assertThat(d1).isEqualTo(d2);
    }

    @Test
    void rejects_negative_field_and_falls_back() {
        when(llmProvider.completarPrompt(anyString()))
            .thenReturn(LlmCompletion.of(
                "{\"precio_venta\":-100,\"produccion_planificada\":1000," +
                "\"inversion_marketing\":100,\"inversion_id\":100," +
                "\"cantidad_empleados\":5,\"salario_promedio\":2700000," +
                "\"prestamo_solicitado\":0,\"inversion_financiera\":0}"));

        BotDecisionDTO decision = strategy.generate(sampleContext());
        assertThat(strategy.lastResult().outcome())
            .isEqualTo(LlmBotStrategy.Outcome.LLM_FALLBACK);
        assertThat(decision.precioUnitario()).isPositive();
    }

    @Test
    void handles_markdown_code_fences_around_json() {
        String wrapped = "```json\n" +
            "{\"precio_venta\":14000,\"produccion_planificada\":1500," +
            "\"inversion_marketing\":1000000,\"inversion_id\":500000," +
            "\"cantidad_empleados\":4,\"salario_promedio\":2700000," +
            "\"prestamo_solicitado\":0,\"inversion_financiera\":0}\n" +
            "```";
        when(llmProvider.completarPrompt(anyString()))
            .thenReturn(LlmCompletion.of(wrapped));

        BotDecisionDTO d = strategy.generate(sampleContext());
        assertThat(d.precioUnitario()).isEqualTo(14_000L);
        assertThat(strategy.lastResult().outcome())
            .isEqualTo(LlmBotStrategy.Outcome.LLM);
    }
}

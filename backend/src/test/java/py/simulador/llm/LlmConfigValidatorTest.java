package py.simulador.llm;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LlmConfigValidatorTest {

    @Test
    void template_provider_no_warning() {
        var props = new LlmProperties("template", "", "");
        Optional<String> result = LlmConfigValidator.mensajeAdvertencia(props);
        assertThat(result).isEmpty();
    }

    @Test
    void real_provider_empty_api_key_warns() {
        var props = new LlmProperties("gemini", "", "gemini-2.5-flash");
        Optional<String> result = LlmConfigValidator.mensajeAdvertencia(props);
        assertThat(result).isPresent();
        assertThat(result.get()).contains("LLM_API_KEY");
        assertThat(result.get()).contains("gemini");
    }

    @Test
    void real_provider_blank_api_key_warns() {
        var props = new LlmProperties("gemini", "  ", "");
        Optional<String> result = LlmConfigValidator.mensajeAdvertencia(props);
        assertThat(result).isPresent();
        assertThat(result.get()).contains("LLM_API_KEY");
        assertThat(result.get()).contains("gemini");
    }

    @Test
    void real_provider_null_api_key_warns() {
        // pin the null branch: isBlank() never runs when apiKey is null (short-circuit)
        var props = new LlmProperties("openai", null, "");
        Optional<String> result = LlmConfigValidator.mensajeAdvertencia(props);
        assertThat(result).isPresent();
        assertThat(result.get()).contains("LLM_API_KEY");
        assertThat(result.get()).contains("openai");
    }

    @Test
    void real_provider_with_key_no_warning() {
        var props = new LlmProperties("gemini", "secret-key", "");
        Optional<String> result = LlmConfigValidator.mensajeAdvertencia(props);
        assertThat(result).isEmpty();
    }

    @Test
    void null_provider_maps_to_template_no_warning() {
        // compact constructor maps null → "template"
        var props = new LlmProperties(null, "", "");
        Optional<String> result = LlmConfigValidator.mensajeAdvertencia(props);
        assertThat(result).isEmpty();
    }

    @Test
    void template_case_insensitive_no_warning() {
        var props = new LlmProperties("TEMPLATE", "", "");
        Optional<String> result = LlmConfigValidator.mensajeAdvertencia(props);
        assertThat(result).isEmpty();
    }
}

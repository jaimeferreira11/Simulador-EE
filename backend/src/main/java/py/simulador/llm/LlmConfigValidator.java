package py.simulador.llm;

import jakarta.annotation.PostConstruct;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Valida la configuración del LLM al arranque. Si se seleccionó un provider real
 * (openai/anthropic/gemini) sin LLM_API_KEY, las funciones de IA caerían en silencio
 * al modo template/FAQ; este validador lo hace explícito con un WARN. No detiene la app.
 */
@Component
public class LlmConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(LlmConfigValidator.class);

    private final LlmProperties props;

    LlmConfigValidator(LlmProperties props) {
        this.props = props;
    }

    @PostConstruct
    void validar() {
        mensajeAdvertencia(props).ifPresentOrElse(
            log::warn,
            () -> log.info(
                "LLM configurado: provider={} model={}",
                props.provider(),
                (props.model() == null || props.model().isBlank()) ? "(default del provider)" : props.model()));
    }

    /**
     * Devuelve un mensaje de advertencia si hay un provider real sin API key; vacío si la
     * configuración es coherente (template, o provider real con key).
     */
    static Optional<String> mensajeAdvertencia(LlmProperties props) {
        String provider = props.provider();
        boolean esTemplate = provider == null || provider.isBlank() || "template".equalsIgnoreCase(provider);
        if (esTemplate) {
            return Optional.empty();
        }
        String apiKey = props.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.of(
                ("LLM_PROVIDER=%s configurado pero LLM_API_KEY está vacío: las funciones de IA "
                    + "(narrativa de eventos, coaching y asistente) caerán a modo template/FAQ. "
                    + "Configurá LLM_API_KEY para habilitar el LLM real.").formatted(provider));
        }
        return Optional.empty();
    }
}

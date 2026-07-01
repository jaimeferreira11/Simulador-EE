package py.simulador.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simulador.llm")
public record LlmProperties(
    String provider,
    String apiKey,
    String model
) {
    public LlmProperties {
        if (provider == null) provider = "template";
    }
}

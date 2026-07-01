package py.simulador.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simulador.app")
public record AppProperties(String baseUrl, String mailFrom) {}

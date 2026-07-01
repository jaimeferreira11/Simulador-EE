package py.simulador.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "simulador.jwt")
public record JwtProperties(
        String secret,
        Duration accessExpiration,
        Duration refreshExpiration
) {}

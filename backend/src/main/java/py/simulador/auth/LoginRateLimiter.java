package py.simulador.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LoginRateLimiter {

    private final Cache<String, AtomicInteger> attempts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    private static final int MAX_ATTEMPTS = 10;

    public boolean isBlocked(String ip) {
        AtomicInteger count = attempts.getIfPresent(ip);
        return count != null && count.get() >= MAX_ATTEMPTS;
    }

    public void recordFailedAttempt(String ip) {
        attempts.asMap()
                .computeIfAbsent(ip, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    public void resetAttempts(String ip) {
        attempts.invalidate(ip);
    }
}

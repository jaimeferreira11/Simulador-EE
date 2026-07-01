package py.simulador.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Executor dedicado para tareas asincrónicas de bots EXPERTO (LLM-driven).
 *
 * <p>Reemplaza el {@code SimpleAsyncTaskExecutor} por defecto de Spring por
 * un pool acotado, con cola y rechazo explícito. Esto permite controlar la
 * concurrencia hacia el proveedor LLM (rate limits, costos, latencia) y
 * evita la creación ilimitada de threads en picos de carga.
 *
 * <p>Defaults: core=2, max=8, queue=50, prefix="bot-llm-", policy=AbortPolicy.
 */
@Configuration
public class BotAsyncConfig {

    public static final String BOT_EXECUTOR_BEAN = "botExecutor";

    @Bean(BOT_EXECUTOR_BEAN)
    public ThreadPoolTaskExecutor botExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("bot-llm-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

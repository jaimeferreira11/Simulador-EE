package py.simulador.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que el bean {@code botExecutor} se construye con la configuración
 * esperada para tareas asincrónicas de bots EXPERTO.
 */
class BotAsyncConfigTest {

    private ThreadPoolTaskExecutor executor;

    @AfterEach
    void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void botExecutor_seConstruyeConDefaultsEsperados() {
        BotAsyncConfig config = new BotAsyncConfig();
        executor = config.botExecutor();

        assertThat(executor).as("bean botExecutor debe construirse").isNotNull();
        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        assertThat(executor.getMaxPoolSize()).isEqualTo(8);
        assertThat(executor.getQueueCapacity()).isEqualTo(50);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("bot-llm-");
        // Rejection policy debe ser AbortPolicy (default safe — rechaza si pool y cola llenos)
        assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
    }

    @Test
    void beanName_esBotExecutor() {
        // Constante usada por @Async("botExecutor") en BotDecisionService
        assertThat(BotAsyncConfig.BOT_EXECUTOR_BEAN).isEqualTo("botExecutor");
    }
}

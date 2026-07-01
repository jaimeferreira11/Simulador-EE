package py.simulador.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NarrativaService {
    private static final Logger log = LoggerFactory.getLogger(NarrativaService.class);

    private final LlmProvider provider;
    private final TemplateProvider fallback = new TemplateProvider();

    public NarrativaService(LlmProvider provider) {
        this.provider = provider;
        log.info("NarrativaService initialized with provider: {}", provider.getClass().getSimpleName());
    }

    public String generarNarrativa(EventoContext ctx) {
        try {
            return provider.generarNarrativa(ctx);
        } catch (Exception e) {
            log.warn("LLM narrative generation failed, using fallback: {}", e.getMessage());
            return fallback.generarNarrativa(ctx);
        }
    }

    public String generarCoaching(CoachingContext ctx) {
        try {
            return provider.generarCoaching(ctx);
        } catch (Exception e) {
            log.warn("LLM coaching generation failed, using fallback: {}", e.getMessage());
            return fallback.generarCoaching(ctx);
        }
    }
}

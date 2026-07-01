package py.simulador.llm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
@ConditionalOnProperty(name = "simulador.llm.provider", havingValue = "template", matchIfMissing = true)
public class TemplateProvider implements LlmProvider {

    @Override
    public String generarNarrativa(EventoContext ctx) {
        return ctx.eventoDescripcion();
    }

    /**
     * Fallback determinístico para completarPrompt: devuelve un JSON neutro,
     * usado solo cuando el orquestador del bot fuerza este provider en modo
     * template (sin LLM real). En la práctica, {@code LlmBotStrategy} captura
     * cualquier excepción y delega a la heurística — este método simplemente
     * existe para que el contrato no rompa cuando el provider configurado es
     * "template".
     */
    @Override
    public LlmCompletion completarPrompt(String prompt) {
        // Devolvemos un JSON sintáctico vacío para que el caller, si intenta
        // parsearlo como decisión de bot, falle el sanity-check y caiga al
        // fallback heurístico de manera predecible.
        return LlmCompletion.of("{}");
    }

    @Override
    public String generarCoaching(CoachingContext ctx) {
        BigDecimal sharePct = ctx.marketShare().multiply(BigDecimal.valueOf(100));
        return String.format(
            "Resultados Q%d para %s: Tu equipo ocupo la posicion %d de %d con un market share de %s%%. " +
            "Los ingresos fueron Gs. %,d con costos operativos de Gs. %,d, resultando en una utilidad neta de Gs. %,d. " +
            "El PIP acumulado es %s puntos.",
            ctx.trimestreNumero(), ctx.equipoNombre(),
            ctx.posicion(), ctx.totalEquipos(),
            sharePct.toPlainString(),
            ctx.ingresos(), ctx.costosOperativos(), ctx.utilidadNeta(),
            ctx.pip().toPlainString()
        );
    }
}

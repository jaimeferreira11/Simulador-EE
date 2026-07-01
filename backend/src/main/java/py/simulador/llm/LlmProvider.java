package py.simulador.llm;

public interface LlmProvider {
    String generarNarrativa(EventoContext ctx);
    String generarCoaching(CoachingContext ctx);

    /**
     * Completion genérico a partir de un prompt arbitrario.
     *
     * <p>Usado por flujos como {@code LlmBotStrategy} (Fase 2 — equipos bot
     * EXPERTO) que arman su propio prompt y necesitan el texto crudo de
     * regreso, sin pasar por los templates de narrativa o coaching.
     *
     * <p>Implementación por defecto lanza {@link UnsupportedOperationException}
     * — los providers que soporten esta vía deben sobreescribir el método.
     *
     * @param prompt el prompt completo a enviar al modelo
     * @return el contenido textual + métricas de uso (tokens si el provider las expone)
     */
    default LlmCompletion completarPrompt(String prompt) {
        throw new UnsupportedOperationException(
            "completarPrompt no implementado por " + getClass().getSimpleName());
    }
}

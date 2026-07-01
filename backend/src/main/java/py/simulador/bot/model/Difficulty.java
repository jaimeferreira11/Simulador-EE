package py.simulador.bot.model;

public enum Difficulty {
    FACIL,
    MEDIO,
    DIFICIL,
    /**
     * Fase 2 — bot jugado por LLM ({@code LlmBotStrategy}).
     * Si el LLM falla o devuelve JSON inválido, se hace fallback a una
     * heurística DIFICIL con la misma personalidad.
     */
    EXPERTO;
}

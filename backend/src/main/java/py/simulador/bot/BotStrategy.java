package py.simulador.bot;

public interface BotStrategy {
    /**
     * Genera una decisión válida dado un contexto inmutable.
     * Debe ser determinístico: misma entrada → misma salida.
     */
    BotDecisionDTO generate(BotContext context);
}

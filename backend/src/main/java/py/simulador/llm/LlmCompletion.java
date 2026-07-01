package py.simulador.llm;

/**
 * Resultado de un completion genérico de un proveedor LLM.
 *
 * <p>Devuelto por {@link LlmProvider#completarPrompt(String)} para uso en flujos
 * que necesitan el texto crudo + métricas de uso (auditoría de tokens / latencia
 * en {@code bot_decision_log}, etc).
 *
 * <p>Las cuentas de tokens son nullable porque algunos proveedores
 * (ej. {@link TemplateProvider}) no las exponen.
 *
 * @param content          texto generado por el modelo
 * @param promptTokens     tokens de entrada consumidos (nullable si no informa el provider)
 * @param completionTokens tokens de salida producidos (nullable si no informa el provider)
 */
public record LlmCompletion(
    String content,
    Integer promptTokens,
    Integer completionTokens
) {
    public static LlmCompletion of(String content) {
        return new LlmCompletion(content, null, null);
    }
}

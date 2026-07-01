package py.simulador.bot.strategy;

import py.simulador.bot.BotContext;
import py.simulador.bot.model.Personality;

import java.util.Locale;
import java.util.Map;

/**
 * Construye el prompt enviado al LLM para los bots EXPERTO (Fase 2).
 *
 * <p>Pure utility — no Spring bean. Toda la lógica vive en {@link #build(BotContext)}
 * para que los tests puedan asertar la estructura del prompt sin levantar contexto
 * Spring ni mockear providers.
 *
 * <p>El prompt incluye, por orden: rol del modelo, personalidad + descripción,
 * estado del propio equipo, contexto macro, eventos activos, competidores,
 * e instrucciones explícitas de devolver SOLO un JSON con campos enteros.
 */
public final class LlmPromptBuilder {

    private LlmPromptBuilder() {}

    public static String build(BotContext ctx) {
        StringBuilder sb = new StringBuilder(2048);

        sb.append("Sos un competidor en una simulacion de negocios paraguaya, ")
          .append("jugando como una empresa de tipo retail/conveniencia.\n\n");

        sb.append("PERSONALIDAD: ").append(ctx.personality())
          .append(" (").append(personalityDescription(ctx.personality())).append(")\n");
        sb.append("NIVEL: EXPERTO (juega lo mejor posible, reacciona al contexto)\n\n");

        sb.append("ESTADO ACTUAL TRIMESTRE ").append(ctx.trimestreId()).append(":\n");
        sb.append("- Caja: Gs. ").append(formatGuaranies(ctx.cajaActual())).append("\n");
        sb.append("- Inventario: ").append(ctx.inventarioUnidades()).append(" unidades\n");
        sb.append("- Brand equity: ")
          .append(String.format(Locale.US, "%.2f", ctx.brandEquity()))
          .append("/1.00\n");
        sb.append("- I+D acumulado: Gs. ").append(formatGuaranies(ctx.rdAcumulado())).append("\n");
        sb.append("- Costo unitario estimado: Gs. ")
          .append(formatGuaranies(ctx.costoUnitarioEstimado())).append("\n");

        if (!ctx.esPrimerTrimestre()) {
            sb.append("- Trimestre anterior: ingreso Gs. ")
              .append(formatGuaranies(ctx.ingresoTrimestreAnterior()))
              .append(", ganancia Gs. ")
              .append(formatGuaranies(ctx.gananciaTrimestreAnterior()))
              .append(", posicion ").append(ctx.posicionRankingAnterior())
              .append("/").append(ctx.totalEquipos()).append("\n");
        } else {
            sb.append("- Es el primer trimestre (sin historial previo)\n");
        }

        sb.append("\nCONTEXTO MACRO:\n");
        if (ctx.parametrosMacro() == null || ctx.parametrosMacro().isEmpty()) {
            sb.append("- (no informado)\n");
        } else {
            for (Map.Entry<String, Number> e : ctx.parametrosMacro().entrySet()) {
                sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
        }

        sb.append("\nEVENTOS ACTIVOS:\n");
        if (ctx.eventosActivos() == null || ctx.eventosActivos().isEmpty()) {
            sb.append("- (ninguno)\n");
        } else {
            for (BotContext.EventoActivo ev : ctx.eventosActivos()) {
                sb.append("- ").append(ev.codigo()).append(": ").append(ev.descripcion());
                if (ev.efectos() != null && !ev.efectos().isEmpty()) {
                    sb.append(" [efectos: ").append(ev.efectos()).append("]");
                }
                sb.append("\n");
            }
        }

        sb.append("\nCOMPETIDORES (posicion previa):\n");
        if (ctx.competidores() == null || ctx.competidores().isEmpty()) {
            sb.append("- (sin competidores)\n");
        } else {
            for (BotContext.Competidor c : ctx.competidores()) {
                sb.append("- ").append(c.nombre())
                  .append(" [").append(c.tipo()).append("]");
                if (c.posicionAnterior() != null) {
                    sb.append(" pos=").append(c.posicionAnterior());
                }
                sb.append("\n");
            }
        }

        sb.append("\nINSTRUCCIONES:\n");
        sb.append("Devolve SOLO un JSON valido con estos campos exactos ")
          .append("(todos enteros positivos, en guaranies o unidades):\n");
        sb.append("{\n");
        sb.append("  \"precio_venta\": <int>,\n");
        sb.append("  \"produccion_planificada\": <int>,\n");
        sb.append("  \"inversion_marketing\": <int>,\n");
        sb.append("  \"inversion_id\": <int>,\n");
        sb.append("  \"cantidad_empleados\": <int>,\n");
        sb.append("  \"salario_promedio\": <int>,\n");
        sb.append("  \"prestamo_solicitado\": <int>,\n");
        sb.append("  \"inversion_financiera\": <int>\n");
        sb.append("}\n\n");
        sb.append("No agregues explicaciones, comentarios ni markdown — solo el JSON crudo.\n");

        return sb.toString();
    }

    private static String personalityDescription(Personality p) {
        return switch (p) {
            case COST_LEADER -> "lider en costos: precio bajo, alto volumen, marketing e I+D conservadores";
            case PREMIUM     -> "jugador premium: precio alto, marca fuerte, alta inversion en calidad e I+D";
            case BALANCEADO  -> "balanceado: equilibrio entre precio, calidad y marketing";
        };
    }

    private static String formatGuaranies(Number n) {
        if (n == null) return "0";
        return String.format(Locale.US, "%,d", n.longValue());
    }
}

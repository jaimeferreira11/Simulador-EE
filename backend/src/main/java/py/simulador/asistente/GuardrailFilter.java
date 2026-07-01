package py.simulador.asistente;

import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

/**
 * Red secundaria best-effort: si la respuesta del LLM parece prescribir una decisión
 * (un monto en guaraníes junto a un verbo de recomendación), agrega una nota aclaratoria.
 * No redacta ni borra. El guardrail principal es el prompt.
 */
@Component
public class GuardrailFilter {

    public static final String NOTA =
            "Nota: el asistente no recomienda precios ni decisiones; la decisión es de tu equipo.";

    private static final Pattern MONTO = Pattern.compile(
            "(?i)\\bgs\\.?\\s*\\d|\\bg\\.\\s*\\d|\\bguaran[ií]");
    private static final Pattern RECOMENDACION = Pattern.compile(
            "(?i)\\bpon[eé]|deber[ií]as|te conviene|conviene (poner|fijar|subir|bajar)|recomiendo|"
            + "sub[ií] el precio|baj[aá] el precio|fij[aá] el precio");

    public String revisar(String respuesta) {
        if (respuesta == null) return null;
        boolean prescriptiva = MONTO.matcher(respuesta).find()
                && RECOMENDACION.matcher(respuesta).find();
        if (prescriptiva && !respuesta.contains(NOTA)) {
            return respuesta + "\n\n" + NOTA;
        }
        return respuesta;
    }
}

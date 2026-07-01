package py.simulador.asistente;

import java.text.Normalizer;
import java.util.Locale;

/** Normalización de texto compartida por el matching del asistente: minúsculas + sin acentos. */
public final class TextoNormalizador {

    private TextoNormalizador() {}

    public static String normalizar(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }
}

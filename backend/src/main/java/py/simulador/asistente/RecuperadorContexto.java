package py.simulador.asistente;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Recuperación léxica en memoria: puntúa cada fragmento por solapamiento de términos con la
 * pregunta y devuelve el top-k concatenado. Si nada matchea, devuelve un default útil
 * (glosario + límites).
 */
@Component
public class RecuperadorContexto {

    public static final int K = 4;

    private final AsistenteBaseConocimiento base;

    public RecuperadorContexto(AsistenteBaseConocimiento base) {
        this.base = base;
    }

    public String recuperar(String pregunta, int k) {
        List<Fragmento> fragmentos = base.fragmentos();
        Set<String> tokens = tokenizar(pregunta);

        List<Fragmento> elegidos = fragmentos.stream()
                .filter(f -> score(f, tokens) > 0)
                .sorted((a, b) -> Integer.compare(score(b, tokens), score(a, tokens)))
                .limit(k)
                .toList();

        if (elegidos.isEmpty()) {
            elegidos = porDefecto(fragmentos, k);
        }

        return elegidos.stream()
                .map(f -> "## " + f.titulo() + "\n" + f.texto())
                .collect(Collectors.joining("\n\n"));
    }

    private int score(Fragmento f, Set<String> tokens) {
        String texto = TextoNormalizador.normalizar(f.titulo() + " " + f.texto());
        int s = 0;
        for (String t : tokens) {
            if (texto.contains(t)) s++;
        }
        return s;
    }

    private Set<String> tokenizar(String pregunta) {
        return Arrays.stream(TextoNormalizador.normalizar(pregunta).split("\\W+"))
                .filter(t -> t.length() > 2)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<Fragmento> porDefecto(List<Fragmento> fragmentos, int k) {
        List<Fragmento> def = fragmentos.stream()
                .filter(f -> f.titulo().contains("Glosario de términos")
                        || f.titulo().contains("Resumen de límites"))
                .toList();
        return def.isEmpty() ? fragmentos.stream().limit(k).toList() : def.stream().limit(k).toList();
    }
}

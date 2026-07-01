package py.simulador.asistente;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Ensambla (una sola vez, cacheado) la base de conocimiento del asistente como fragmentos
 * recuperables: una sección por encabezado del manual del jugador (incluye el glosario)
 * + un fragmento por cada FAQ activa.
 */
@Component
public class AsistenteBaseConocimiento {

    private final AsistenteFaqRepository faqRepo;
    private volatile List<Fragmento> cacheFragmentos;

    public AsistenteBaseConocimiento(AsistenteFaqRepository faqRepo) {
        this.faqRepo = faqRepo;
    }

    public List<Fragmento> fragmentos() {
        List<Fragmento> local = cacheFragmentos;
        if (local != null) return local;
        synchronized (this) {
            if (cacheFragmentos != null) return cacheFragmentos;
            List<Fragmento> frags = new ArrayList<>(trocearManual(leerManual()));
            for (AsistenteFaqEntity f : faqRepo.findByActivaTrueOrderByOrdenAsc()) {
                frags.add(new Fragmento(f.getPregunta(),
                        "P: " + f.getPregunta() + "\nR: " + f.getRespuesta()));
            }
            cacheFragmentos = List.copyOf(frags);
            return cacheFragmentos;
        }
    }

    /** Trocea el markdown por encabezado de nivel 2 ("## "). El preámbulo previo al primer
     *  encabezado se adjunta al cuerpo del primer fragmento para no perderlo. */
    private List<Fragmento> trocearManual(String manual) {
        List<Fragmento> secciones = new ArrayList<>();
        StringBuilder preambulo = new StringBuilder();
        String titulo = null;
        StringBuilder cuerpo = new StringBuilder();
        boolean primeraSeccion = true;

        for (String linea : manual.split("\n", -1)) {
            if (linea.startsWith("## ")) {
                if (titulo != null) {
                    secciones.add(new Fragmento(titulo, cuerpo.toString().strip()));
                }
                titulo = linea.substring(3).strip();
                cuerpo = new StringBuilder();
                if (primeraSeccion && preambulo.length() > 0) {
                    cuerpo.append(preambulo).append("\n");
                    primeraSeccion = false;
                }
            } else if (titulo == null) {
                preambulo.append(linea).append("\n");
            } else {
                cuerpo.append(linea).append("\n");
            }
        }
        if (titulo != null) {
            secciones.add(new Fragmento(titulo, cuerpo.toString().strip()));
        }
        return secciones;
    }

    private String leerManual() {
        try (InputStream is = new ClassPathResource("asistente/guia-jugador.md").getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el manual del asistente", e);
        }
    }
}

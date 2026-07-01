package py.simulador.asistente;

import org.springframework.stereotype.Component;
import py.simulador.asistente.dto.AsistenteContexto;
import py.simulador.asistente.dto.OrigenRespuesta;
import py.simulador.asistente.dto.RespuestaAsistente;
import py.simulador.asistente.dto.RespuestaAsistente.Fuente;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Component
public class AsistenteFaqProvider implements AsistenteProvider {

    private final AsistenteFaqRepository faqRepo;

    public AsistenteFaqProvider(AsistenteFaqRepository faqRepo) {
        this.faqRepo = faqRepo;
    }

    @Override
    public RespuestaAsistente responder(AsistenteContexto ctx) {
        List<AsistenteFaqEntity> faqs = faqRepo.findByActivaTrueOrderByOrdenAsc();
        String preguntaNorm = normalizar(ctx.pregunta());

        AsistenteFaqEntity mejor = null;
        int mejorScore = 0;
        for (AsistenteFaqEntity faq : faqs) {
            int score = 0;
            if (faq.getKeywords() != null) {
                for (String kw : faq.getKeywords()) {
                    if (!kw.isBlank() && preguntaNorm.contains(normalizar(kw))) {
                        score++;
                    }
                }
            }
            if (score > mejorScore) {
                mejorScore = score;
                mejor = faq;
            }
        }

        if (mejor != null && mejorScore > 0) {
            final AsistenteFaqEntity elegido = mejor;
            List<String> relacionadas = faqs.stream()
                    .filter(f -> f != elegido)
                    .limit(3)
                    .map(AsistenteFaqEntity::getPregunta)
                    .toList();
            return new RespuestaAsistente(
                    elegido.getRespuesta(),
                    List.of(new Fuente("Manual: " + elegido.getSeccionManual(),
                            elegido.getSeccionManual())),
                    relacionadas,
                    OrigenRespuesta.FAQ,
                    elegido.getId());
        }

        // Fallback: sin coincidencias.
        List<String> sugerencias = faqs.stream()
                .limit(3).map(AsistenteFaqEntity::getPregunta).toList();
        return new RespuestaAsistente(
                "No encontré una respuesta exacta a tu pregunta. Revisá el Manual o el Glosario, "
                        + "o probá con una de estas preguntas frecuentes.",
                List.of(new Fuente("Manual del jugador", "Guía paso a paso para Jugador"),
                        new Fuente("Glosario de términos", "Glosario de términos")),
                sugerencias,
                OrigenRespuesta.FALLBACK);
    }

    /** minúsculas + sin acentos, para matching robusto. */
    static String normalizar(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }
}

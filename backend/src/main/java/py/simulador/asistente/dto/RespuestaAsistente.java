package py.simulador.asistente.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RespuestaAsistente(
        @JsonProperty("texto") String texto,
        @JsonProperty("fuentes") List<Fuente> fuentes,
        @JsonProperty("relacionadas") List<String> relacionadas,
        @JsonProperty("origen") OrigenRespuesta origen,
        // Id de la FAQ que resolvió la consulta (auditoría). No se serializa al cliente.
        @JsonIgnore Long faqId
) {
    /** Conveniencia: respuestas sin FAQ asociada (RAG, fallback, etc.). */
    public RespuestaAsistente(String texto, List<Fuente> fuentes, List<String> relacionadas,
                              OrigenRespuesta origen) {
        this(texto, fuentes, relacionadas, origen, null);
    }

    public record Fuente(
            @JsonProperty("titulo") String titulo,
            @JsonProperty("ancla_manual") String anclaManual
    ) {}
}

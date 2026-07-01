package py.simulador.asistente.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PreguntaRequest(
        @JsonProperty("pregunta")
        @NotBlank @Size(max = 500)
        String pregunta
) {}

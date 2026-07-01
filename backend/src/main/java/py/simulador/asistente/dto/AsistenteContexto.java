package py.simulador.asistente.dto;

public record AsistenteContexto(
        String pregunta,
        Long competenciaId,
        Long equipoId   // Fase 3: contexto del equipo. Null en Fase 1.
) {}

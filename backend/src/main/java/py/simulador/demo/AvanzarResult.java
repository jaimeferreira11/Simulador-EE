package py.simulador.demo;

public record AvanzarResult(
    Long trimestreAnteriorId,
    Long trimestreActualId,   // null if last quarter
    String competenciaEstado
) {
    public Long getTrimestreAnteriorId() { return trimestreAnteriorId; }
    public Long getTrimestreActualId()   { return trimestreActualId; }
    public String getCompetenciaEstado() { return competenciaEstado; }
}

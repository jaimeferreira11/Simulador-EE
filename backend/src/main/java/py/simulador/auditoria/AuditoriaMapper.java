package py.simulador.auditoria;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import py.simulador.api.generated.model.AuditoriaDecision;
import py.simulador.api.generated.model.AuditoriaEvento;
import py.simulador.common.JsonNullableMapper;
import org.openapitools.jackson.nullable.JsonNullable;
import py.simulador.decision.DecisionCampoLogEntity;

import java.util.Map;

@Mapper(componentModel = "spring", uses = JsonNullableMapper.class)
public interface AuditoriaMapper {

    @Mapping(target = "accion", expression = "java(AuditoriaDecision.AccionEnum.fromValue(entity.getAccion()))")
    @Mapping(target = "estadoAnterior", expression = "java(org.openapitools.jackson.nullable.JsonNullable.of(entity.getEstadoAnterior()))")
    @Mapping(target = "estadoNuevo", source = "estadoNuevo")
    AuditoriaDecision toDecisionDto(AuditoriaDecisionEntity entity);

    @Mapping(target = "datosContexto", expression = "java(org.openapitools.jackson.nullable.JsonNullable.of(entity.getDatosContexto()))")
    AuditoriaEvento toEventoDto(AuditoriaEventoEntity entity);

    /**
     * Mapea un registro de decision_campo_log al DTO AuditoriaDecision.
     * Usa estadoAnterior/estadoNuevo como maps {campo: valor} para el detalle por campo.
     */
    default AuditoriaDecision toCampoLogDto(DecisionCampoLogEntity log) {
        AuditoriaDecision dto = new AuditoriaDecision();
        dto.setId(log.getId());
        dto.setDecisionEquipoId(log.getDecisionEquipoId());
        dto.setUsuarioId(log.getUsuarioId());
        dto.setAccion(AuditoriaDecision.AccionEnum.MODIFICADA);
        dto.setEstadoAnterior(JsonNullable.of(Map.of(log.getCampo(), log.getValorAnterior() != null ? log.getValorAnterior() : "")));
        dto.setEstadoNuevo(Map.of(log.getCampo(), log.getValorNuevo() != null ? log.getValorNuevo() : ""));
        dto.setOcurridoAt(log.getModificadoAt());
        return dto;
    }
}

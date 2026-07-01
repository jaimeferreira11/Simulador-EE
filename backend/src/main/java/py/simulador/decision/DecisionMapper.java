package py.simulador.decision;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import py.simulador.api.generated.model.Decision;
import py.simulador.common.JsonNullableMapper;

@Mapper(componentModel = "spring", uses = JsonNullableMapper.class)
public interface DecisionMapper {

    @Mapping(target = "estado", expression = "java(Decision.EstadoEnum.fromValue(entity.getEstado()))")
    @Mapping(target = "aumentoSalarialPct", expression = "java(entity.getAumentoSalarialPct() != null ? entity.getAumentoSalarialPct().floatValue() : 0f)")
    Decision toDto(DecisionEquipoEntity entity);
}

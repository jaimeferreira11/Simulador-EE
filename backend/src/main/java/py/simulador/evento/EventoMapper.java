package py.simulador.evento;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.openapitools.jackson.nullable.JsonNullable;
import py.simulador.api.generated.model.EventoCompetencia;
import py.simulador.common.JsonNullableMapper;

@Mapper(componentModel = "spring", uses = JsonNullableMapper.class)
public interface EventoMapper {

    @Mapping(target = "origen", expression = "java(EventoCompetencia.OrigenEnum.fromValue(entity.getOrigen()))")
    @Mapping(target = "magnitudAplicada", expression = "java(entity.getMagnitudAplicada() != null ? entity.getMagnitudAplicada().floatValue() : 0f)")
    EventoCompetencia toDto(EventoCompetenciaEntity entity);
}

package py.simulador.trimestre;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import py.simulador.api.generated.model.Trimestre;
import py.simulador.api.generated.model.TrimestreProcesado;
import py.simulador.common.JsonNullableMapper;

@Mapper(componentModel = "spring", uses = JsonNullableMapper.class)
public interface TrimestreMapper {

    @Mapping(target = "estado", expression = "java(Trimestre.EstadoEnum.fromValue(entity.getEstado()))")
    Trimestre toDto(TrimestreEntity entity);

    @Mapping(target = "estado", expression = "java(TrimestreProcesado.EstadoEnum.fromValue(entity.getEstado()))")
    TrimestreProcesado toProcesadoDto(TrimestreEntity entity);
}

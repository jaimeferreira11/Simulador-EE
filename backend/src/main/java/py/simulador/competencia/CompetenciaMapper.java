package py.simulador.competencia;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import py.simulador.api.generated.model.Competencia;
import py.simulador.api.generated.model.CompetenciaDetalle;
import py.simulador.api.generated.model.CompetenciaPublica;
import py.simulador.common.JsonNullableMapper;

@Mapper(componentModel = "spring", uses = JsonNullableMapper.class)
public interface CompetenciaMapper {

    @Mapping(target = "estado", expression = "java(Competencia.EstadoEnum.fromValue(entity.getEstado()))")
    Competencia toDto(CompetenciaEntity entity);

    @Mapping(target = "estado", expression = "java(CompetenciaDetalle.EstadoEnum.fromValue(entity.getEstado()))")
    CompetenciaDetalle toDetalleDto(CompetenciaEntity entity);

    @Mapping(target = "estado", source = "estado")
    CompetenciaPublica toPublicaDto(CompetenciaEntity entity);
}

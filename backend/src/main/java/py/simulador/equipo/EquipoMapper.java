package py.simulador.equipo;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import py.simulador.api.generated.model.Equipo;
import py.simulador.api.generated.model.EquipoDetalle;
import py.simulador.api.generated.model.EquipoMiembro;
import py.simulador.common.JsonNullableMapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = JsonNullableMapper.class)
public interface EquipoMapper {

    @Mapping(target = "estado", expression = "java(Equipo.EstadoEnum.fromValue(entity.getEstado()))")
    @Mapping(target = "tipo", expression = "java(entity.getTipo() != null ? Equipo.TipoEnum.fromValue(entity.getTipo()) : Equipo.TipoEnum.HUMANO)")
    @Mapping(target = "dificultad", expression = "java(entity.getDificultad() != null ? org.openapitools.jackson.nullable.JsonNullable.of(Equipo.DificultadEnum.fromValue(entity.getDificultad())) : org.openapitools.jackson.nullable.JsonNullable.<Equipo.DificultadEnum>undefined())")
    @Mapping(target = "personalidad", expression = "java(org.openapitools.jackson.nullable.JsonNullable.of(entity.getPersonalidad()))")
    Equipo toDto(EquipoEntity entity);

    @Mapping(target = "estado", expression = "java(EquipoDetalle.EstadoEnum.fromValue(entity.getEstado()))")
    @Mapping(target = "tipo", expression = "java(entity.getTipo() != null ? EquipoDetalle.TipoEnum.fromValue(entity.getTipo()) : EquipoDetalle.TipoEnum.HUMANO)")
    @Mapping(target = "dificultad", expression = "java(entity.getDificultad() != null ? org.openapitools.jackson.nullable.JsonNullable.of(EquipoDetalle.DificultadEnum.fromValue(entity.getDificultad())) : org.openapitools.jackson.nullable.JsonNullable.<EquipoDetalle.DificultadEnum>undefined())")
    @Mapping(target = "personalidad", expression = "java(org.openapitools.jackson.nullable.JsonNullable.of(entity.getPersonalidad()))")
    @Mapping(target = "miembros", source = "miembros")
    EquipoDetalle toDetalleDto(EquipoEntity entity, List<EquipoMiembroEntity> miembros);

    EquipoMiembro toMiembroDto(EquipoMiembroEntity entity);
}

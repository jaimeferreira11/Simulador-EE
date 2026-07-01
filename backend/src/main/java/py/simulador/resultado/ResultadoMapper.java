package py.simulador.resultado;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import py.simulador.api.generated.model.*;
import py.simulador.common.JsonNullableMapper;

@Mapper(componentModel = "spring", uses = JsonNullableMapper.class)
public interface ResultadoMapper {

    ResultadoCalculo toResultadoDto(ResultadoCalculoEntity entity);

    @Mapping(target = "momento", expression = "java(SnapshotEstado.MomentoEnum.fromValue(entity.getMomento()))")
    SnapshotEstado toSnapshotDto(SnapshotEstadoEntity entity);

    @Mapping(target = "nombreEmpresa", ignore = true)
    @Mapping(target = "codigoColor", ignore = true)
    @Mapping(target = "posicion", source = "posicion")
    RankingItem toRankingDto(RankingTrimestreEntity entity);
}

package py.simulador.catalogo;

import org.mapstruct.Mapper;
import py.simulador.api.generated.model.EventoCatalogo;
import py.simulador.api.generated.model.ParametroMacro;
import py.simulador.api.generated.model.ParametroRubro;
import py.simulador.api.generated.model.Rubro;
import py.simulador.common.JsonNullableMapper;

@Mapper(componentModel = "spring", uses = JsonNullableMapper.class)
public interface CatalogoMapper {

    Rubro toRubroDto(RubroEntity entity);

    ParametroMacro toParametroMacroDto(ParametroMacroEntity entity);

    ParametroRubro toParametroRubroDto(ParametroRubroEntity entity);

    EventoCatalogo toEventoCatalogoDto(EventoCatalogoEntity entity);
}

package py.simulador.usuario;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import py.simulador.api.generated.model.Usuario;
import py.simulador.auth.RolCache;
import py.simulador.common.JsonNullableMapper;

@Mapper(componentModel = "spring", uses = JsonNullableMapper.class)
public interface UsuarioMapper {

    @Mapping(target = "rol", expression = "java(Usuario.RolEnum.fromValue(rolCache.getCodigo(entity.getRolUsuarioId())))")
    Usuario toDto(UsuarioEntity entity, @Context RolCache rolCache);
}

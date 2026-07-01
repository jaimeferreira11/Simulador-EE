package py.simulador.common;

import org.mapstruct.Mapper;
import org.openapitools.jackson.nullable.JsonNullable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Mapper(componentModel = "spring")
public interface JsonNullableMapper {

    default JsonNullable<OffsetDateTime> wrapDateTime(OffsetDateTime value) {
        return JsonNullable.of(value);
    }

    default JsonNullable<Long> wrapLong(Long value) {
        return JsonNullable.of(value);
    }

    default JsonNullable<Integer> wrapShortToInteger(Short value) {
        return value == null ? JsonNullable.undefined() : JsonNullable.of(value.intValue());
    }

    default JsonNullable<Float> wrapBigDecimalToFloat(BigDecimal value) {
        return value == null ? JsonNullable.undefined() : JsonNullable.of(value.floatValue());
    }

    default JsonNullable<String> wrapString(String value) {
        return JsonNullable.of(value);
    }
}

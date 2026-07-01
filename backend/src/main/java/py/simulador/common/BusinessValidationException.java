package py.simulador.common;

import java.util.List;

public class BusinessValidationException extends RuntimeException {

    private final List<FieldError> errors;

    public BusinessValidationException(String message) {
        super(message);
        this.errors = List.of();
    }

    public BusinessValidationException(String message, List<FieldError> errors) {
        super(message);
        this.errors = errors;
    }

    public List<FieldError> getErrors() { return errors; }

    public record FieldError(String field, String message) {}
}

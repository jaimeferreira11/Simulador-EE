package py.simulador.common;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " con id " + id + " no encontrado");
    }

    public ResourceNotFoundException(String resource, String field, String value) {
        super(resource + " con " + field + " '" + value + "' no encontrado");
    }
}

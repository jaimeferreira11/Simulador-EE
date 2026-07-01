package py.simulador.auth;

public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException() {
        super("Demasiados intentos de login. Intente de nuevo en unos minutos.");
    }
}

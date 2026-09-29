package pe.gob.munisanmiguel.exception;

public class ReniecUnavailableException extends RuntimeException {
    public ReniecUnavailableException(String message) { super(message); }
    public ReniecUnavailableException(String message, Throwable cause) { super(message, cause); }
}

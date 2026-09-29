package pe.gob.munisanmiguel.exception;

public class ReniecNotConfiguredException extends RuntimeException {
    public ReniecNotConfiguredException() { super("Consulta RENIEC no configurada."); }
}

package pe.gob.munisanmiguel.exception;

public class ReniecNotConfiguredException extends RuntimeException {
    public ReniecNotConfiguredException() { super("Proveedor DNI desactivado o sin credencial configurada."); }
    public ReniecNotConfiguredException(String message) { super(message); }
}

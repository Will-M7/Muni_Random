package pe.gob.munisanmiguel.entity;

public enum EstadoSolicitud {
    EN_ESPERA, VERIFICADO, OBSERVADA, EXPIRADO;

    public static EstadoSolicitud fromFrontend(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase().replace('Ó', 'O').replace('Í', 'I').replaceAll("\\s+", "_"));
    }

    public String toFrontend() {
        return switch (this) {
            case EN_ESPERA -> "En espera";
            case VERIFICADO -> "Verificado";
            case OBSERVADA -> "Observada";
            case EXPIRADO -> "Expirado";
        };
    }
}

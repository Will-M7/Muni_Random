package pe.gob.munisanmiguel.dto;

import jakarta.validation.constraints.*;
import pe.gob.munisanmiguel.entity.Rol;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {}
    public record LoginRequest(String usuario, String username, @NotBlank String password, String rol) {
        public String identifier() { return usuario != null && !usuario.isBlank() ? usuario : username; }
    }
    public record LoginResponse(String token, String nombre, String cargo, String correo, String rol, String fiscalizadorId) {}
    public record HistorialResponse(String fecha, String estado, String nota) {}
    public record FiscalizadorResponse(String id, String codigo, String nombre, String zona, String telefono, boolean activo) {}
    public record SolicitudResponse(String codigo, String nombres, String apellidos, String nombre, String dni, String telefono,
                                    String direccion, String referencia, String documento, String documentoTamano,
                                    String ubicacion, BigDecimal latitud, BigDecimal longitud, String fecha, String hora,
                                    LocalDate fechaFiscalizacion, LocalTime horaFiscalizacion,
                                    String fiscalizadorId, String fiscalizadorNombre, String estado, String observaciones,
                                    String resultadoVisita, String fechaRegistro, List<HistorialResponse> historialCambios,
                                    String resultadoFiscalizacion, String observacionesFiscalizador, String reporteNombreOriginal,
                                    Long reporteTamano, LocalDateTime fechaEjecucion) {}
    public record SolicitudRequest(@NotBlank @Pattern(regexp = "\\d{8}") String dni, @NotBlank String nombres,
                                   @NotBlank String apellidos, @NotBlank String telefono, @NotBlank String direccion,
                                   String referencia, String documento, String documentoTamano,
                                   @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitud,
                                   @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitud,
                                   @NotNull LocalDate fecha, @NotBlank String hora, String fiscalizadorId,
                                   LocalDate fechaFiscalizacion, LocalTime horaFiscalizacion,
                                   String estado, String observaciones) {}
    public record EstadoRequest(@NotBlank String estado, String resultado, String observacionAdicional) {}
    public record ReniecResponse(String dni, String nombres, String apellidoPaterno, String apellidoMaterno,
                                 String sexo, LocalDate fechaNacimiento) {}
    public record ProgramacionResponse(String codigo, LocalTime horaFiscalizacion, String ciudadano, String fiscalizador, String estado) {}
    public record FiscalizadorTaskResponse(String codigo, String ciudadano, String dni, String telefono, String direccion,
                                           String referencia, LocalDate fecha, LocalTime hora, BigDecimal latitud, BigDecimal longitud,
                                           String documento, String resultado, String observaciones, String reporteNombreOriginal,
                                           Long reporteTamano, LocalDateTime fechaEjecucion, String estadoSolicitud,
                                           String observacionesMunicipales) {}
    public record ResultadoFiscalizacionRequest(@NotNull pe.gob.munisanmiguel.entity.ResultadoFiscalizacion resultado, String observaciones) {}
    public record UsuarioResponse(String username, String nombre, String rol, boolean activo, String fechaCreacion) {}
    public record UsuarioRequest(@NotBlank String username, @NotBlank String nombre, @NotBlank @Size(min = 8) String password,
                                 @NotBlank String confirmarPassword, @NotNull Rol rol) {}
}

package pe.gob.munisanmiguel.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import pe.gob.munisanmiguel.entity.Rol;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;

public final class ApiDtos {
    private ApiDtos() {}
    public record LoginRequest(String usuario, String username, @NotBlank String password, String rol) {
        public String identifier() { return usuario != null && !usuario.isBlank() ? usuario : username; }
    }
    public record LoginResponse(String token, String nombre, String cargo, String correo, String rol, String fiscalizadorId, List<String> roles, List<String> capacidades) {}
    public record HistorialResponse(String fecha, String estado, String nota) {}
    public record FiscalizadorDisponibleResponse(String id, String nombre, String zona, boolean activo) {}
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
    public record FiscalizadorExpedienteTaskResponse(String codigo, String objetoFiscalizacion, String administrado,
            String documentoAdministrado, String telefono, String direccion, String referencia, LocalDate fecha,
            LocalTime hora, BigDecimal latitud, BigDecimal longitud, String origen, String tipoVisita,
            String estadoProgramacion, String estadoExpediente, Long programacionId, Long diligenciaId,
            String estadoDiligencia, String solicitudCodigo) {}
    public record ResultadoFiscalizacionRequest(@NotNull pe.gob.munisanmiguel.entity.ResultadoFiscalizacion resultado, String observaciones) {}
    public record UsuarioResponse(String username, String nombre, String rol, boolean activo, String fechaCreacion, List<String> roles,String fiscalizadorId,String fiscalizadorNombre) {}
    public record UsuarioRequest(@NotBlank String username, @NotBlank String nombre, @NotBlank @Size(min = 8) String password,
                                 String confirmarPassword, @NotNull Rol rol, String fiscalizadorId) {}
    public record RoleResponse(String nombre, String descripcion, boolean configurable, List<String> capacidades) {}
    public record CapabilityResponse(String codigo, String descripcion, String categoria) {}
    public record RoleCapabilitiesRequest(@NotNull List<String> capacidades) {}
    public record UserRolesRequest(@NotNull List<String> roles,String fiscalizadorId) {}
    public record UserActiveRequest(boolean activo) {}
    public record ExpedienteRequest(@NotNull pe.gob.munisanmiguel.entity.OrigenFiscalizacion origen, String detalleOrigen,
                                    @NotBlank String objetoFiscalizacion, String observacionesIniciales,
                                    String tipoAdministrado, String documentoAdministrado, String nombreAdministrado,
                                    String telefonoContacto, String direccionFiscalizada, String solicitudCodigo,
                                    String administradoApellidos, String referenciaDomicilio,
                                    java.math.BigDecimal latitud, java.math.BigDecimal longitud) {}
    public record CrearFiscalizacionRequest(@NotNull @Valid ExpedienteRequest expediente,
            @Size(max=160) String dependenciaProcedencia,@Size(max=160) String referenciaSolicitud,
            @Valid ProgramacionRequest primeraProgramacion,String metodoUbicacion,String enlaceGoogle) {
        public CrearFiscalizacionRequest(ExpedienteRequest expediente,String dependenciaProcedencia,String referenciaSolicitud,ProgramacionRequest primeraProgramacion){this(expediente,dependenciaProcedencia,referenciaSolicitud,primeraProgramacion,null,null);}
    }
    public record ExpedienteDocumentoResponse(Long id,String nombre,String tipoMime,long tamanoBytes,
            LocalDateTime adjuntadoEn,String adjuntadoPor) {}
    public record ProgramacionRequest(String fiscalizadorId, @NotNull LocalDate fecha, @NotNull LocalTime hora,
                                      @NotNull pe.gob.munisanmiguel.entity.TipoVisita tipoVisita) {}
    public record ReprogramarRequest(@NotNull LocalDate fecha, @NotNull LocalTime hora, @NotBlank String motivo,
                                     String fiscalizadorId, @NotNull pe.gob.munisanmiguel.entity.TipoVisita tipoVisita) {}
    public record CancelarProgramacionRequest(@NotBlank String motivo) {}
    public record ProgramacionFiscalizacionResponse(Long id, LocalDate fecha, LocalTime hora, String tipoVisita,
            String estado, String fiscalizadorId, String fiscalizadorNombre, Long programacionAnteriorId,
            String motivoReprogramacion, String motivoCancelacion, String motivoSeguimiento, Long revisionOrigenId) {}
    public record ExpedienteHistorialResponse(LocalDateTime fechaHora, String actor, String tipoEvento,
            String descripcion, String valoresAnteriores, String valoresNuevos, Long programacionId) {}
    public record ExpedienteResponse(Long id, String codigo, LocalDateTime fechaCreacion, String creadoPor,
            String origen, String detalleOrigen, String dependenciaProcedencia,String referenciaSolicitud,
            String estado, String objetoFiscalizacion,
            String observacionesIniciales, String solicitudCodigo, String tipoAdministrado,
            String documentoAdministrado, String nombreAdministrado, String telefonoContacto,
            String direccionFiscalizada, String administradoApellidos, String referenciaDomicilio,
            java.math.BigDecimal latitud, java.math.BigDecimal longitud, String documentoSustentoNombre,
            LocalDateTime documentoSustentoFecha, String reporteFiscalizadorNombre,
            List<ProgramacionFiscalizacionResponse> programaciones,
            List<ExpedienteHistorialResponse> historial) {}
    public record InicioDiligenciaRequest(pe.gob.munisanmiguel.entity.EstadoGps estadoGps,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitud, @DecimalMin("-180") @DecimalMax("180") BigDecimal longitud) {}
    public record RegistroPosteriorDiligenciaRequest(LocalDate fechaEjecucion, LocalTime horaEjecucion) {}
    public record GuardarDiligenciaRequest(pe.gob.munisanmiguel.entity.SituacionPersona situacionPersona,
            String personaNombres, String personaApellidos, String personaTipoDocumento, String personaNumeroDocumento,
            String personaCondicion, String personaObservaciones, String hechosConstatados, String ocurrencias,
            String observacionesFiscalizador, String observacionesFiscalizado) {}
    public record ParticipanteRequest(@NotNull pe.gob.munisanmiguel.entity.TipoParticipante tipoParticipante,
            @NotBlank String nombre, String apellidos, String tipoDocumento, String numeroDocumento,
            String entidadArea, String cargoCondicion, String observaciones) {}
    public record NoRealizadaRequest(@NotNull pe.gob.munisanmiguel.entity.MotivoNoRealizada motivo,
            String detalleMotivo, String observacionesFiscalizador, String ocurrencias,
            pe.gob.munisanmiguel.entity.EstadoGps estadoGps, @DecimalMin("-90") @DecimalMax("90") BigDecimal latitud,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitud) {}
    public record FinalizarDiligenciaRequest(@NotNull pe.gob.munisanmiguel.entity.EstadoFirmaActa firmaFiscalizado,
            String observacionFirma, boolean copiaEntregada, @NotNull pe.gob.munisanmiguel.entity.MedioEntrega medioEntrega,
            pe.gob.munisanmiguel.entity.EstadoGps estadoGpsCierre, @DecimalMin("-90") @DecimalMax("90") BigDecimal latitudCierre,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitudCierre) {}
    public record ParticipanteResponse(Long id, String tipoParticipante, String nombre, String apellidos,
            String tipoDocumento, String numeroDocumento, String entidadArea, String cargoCondicion,
            String observaciones, LocalDateTime creadoEn, String creadoPor) {}
    public record EvidenciaResponse(Long id, String tipo, String nombreOriginal, String tipoMime,
            long tamanoBytes, String descripcion, LocalDateTime fechaHora, String autor) {}
    public record ActaResumenResponse(Long id, String codigo, String firmaFiscalizadoEstado,
            String pdfGeneradoNombre, String pdfFirmadoNombre, LocalDateTime generadoEn) {}
    public record DiligenciaWorkspaceResponse(Long id, String expedienteCodigo, String origen,
            String objetoFiscalizacion, String observacionesPrevias, String administradoNombre,
            String administradoNombres, String administradoApellidos, String fiscalizadorResponsable,
            String documentoAdministrado, String telefonoContacto, String direccion, String referencia,
            BigDecimal latitudPredio, BigDecimal longitudPredio, Long programacionId, LocalDate fechaProgramada,
            LocalTime horaProgramada, String tipoVisita, String estadoProgramacion, String estadoExpediente,
            String estadoDiligencia, LocalDateTime fechaInicio, LocalDateTime fechaCierre,
            BigDecimal latitudInicio, BigDecimal longitudInicio, String gpsInicioEstado,
            BigDecimal latitudCierre, BigDecimal longitudCierre, String gpsCierreEstado, String lugarDiligencia,
            String situacionPersona, String personaNombres, String personaApellidos, String personaTipoDocumento,
            String personaNumeroDocumento, String personaCondicion, String personaObservaciones,
            String hechosConstatados, String ocurrencias, String observacionesFiscalizador,
            String observacionesFiscalizado, String motivoNoRealizada, String detalleMotivoNoRealizada,
            boolean copiaEntregada, String medioEntrega, String documentoSustentoNombre,
            boolean registroPosterior, LocalDateTime registradoEn, String registradoPor,
            LocalDateTime fechaHoraEjecucion, String origenHoraEjecucion,
            List<ParticipanteResponse> participantes, List<EvidenciaResponse> evidencias,
            ActaResumenResponse acta) {}
    public record ActaResponse(Long id, String codigo, Long diligenciaId, String expedienteCodigo,
            String firmaFiscalizadoEstado, String firmaFiscalizadoObservacion, String pdfGeneradoNombre,
            String pdfFirmadoNombre, LocalDateTime generadoEn, Map<String,Object> contenido) {}
    public record SolicitarAclaracionRequest(@jakarta.validation.constraints.NotBlank String mensaje) {}
    public record ResponderAclaracionRequest(@jakarta.validation.constraints.NotBlank String respuesta) {}
    public record ConcluirRevisionRequest(@NotNull pe.gob.munisanmiguel.entity.ConclusionFiscalizacion conclusion,
            @NotBlank String fundamentoConclusion, String observacionesRevision, String descripcionConclusion,
            String plazoConclusion, boolean requiereSeguimiento, boolean requiereDerivacion,
            String denominacionOtra, String referenciaDocumentos) {}
    public record SeguimientoRequest(@NotNull LocalDate fecha, @NotNull LocalTime hora,
            @NotNull pe.gob.munisanmiguel.entity.TipoVisita tipoVisita, @NotBlank String fiscalizadorId,
            @NotBlank String motivo) {}
}

package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Entity @Table(name = "solicitud")
@Getter @Setter @NoArgsConstructor
public class Solicitud {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 30) private String codigo;
    @Column(nullable = false, length = 120) private String nombres;
    @Column(nullable = false, length = 160) private String apellidos;
    @Column(nullable = false, length = 8) private String dni;
    @Column(nullable = false, length = 30) private String telefono;
    @Column(nullable = false, length = 250) private String direccion;
    @Column(length = 250) private String referencia;
    @Column(nullable = false, length = 255) private String documento;
    @Column(name = "documento_tamano", length = 30) private String documentoTamano;
    @Column(name = "documento_nombre_interno", length = 120) private String documentoNombreInterno;
    @Column(name = "documento_content_type", length = 80) private String documentoContentType;
    @Column(name = "documento_ruta", length = 255) private String documentoRuta;
    @Column(name = "documento_fecha_subida") private java.time.LocalDateTime documentoFechaSubida;
    @Column(nullable = false, precision = 10, scale = 7) private BigDecimal latitud;
    @Column(nullable = false, precision = 10, scale = 7) private BigDecimal longitud;
    @Column(nullable = false) private LocalDate fecha;
    @Column(nullable = false, length = 30) private String hora;
    @Column(name = "fecha_fiscalizacion") private LocalDate fechaFiscalizacion;
    @Column(name = "hora_fiscalizacion") private java.time.LocalTime horaFiscalizacion;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "fiscalizador_id") private Fiscalizador fiscalizador;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private EstadoSolicitud estado;
    @Column(columnDefinition = "TEXT") private String observaciones;
    @Column(name = "resultado_visita", columnDefinition = "TEXT") private String resultadoVisita;
    @Enumerated(EnumType.STRING) @Column(name = "resultado_fiscalizacion", nullable = false, length = 20) private ResultadoFiscalizacion resultadoFiscalizacion = ResultadoFiscalizacion.PENDIENTE;
    @Column(name = "observaciones_fiscalizador", columnDefinition = "TEXT") private String observacionesFiscalizador;
    @Column(name = "reporte_nombre_original", length = 255) private String reporteNombreOriginal;
    @Column(name = "reporte_nombre_interno", length = 120) private String reporteNombreInterno;
    @Column(name = "reporte_content_type", length = 80) private String reporteContentType;
    @Column(name = "reporte_tamano") private Long reporteTamano;
    @Column(name = "reporte_ruta", length = 255) private String reporteRuta;
    @Column(name = "reporte_fecha_subida") private java.time.LocalDateTime reporteFechaSubida;
    @Column(name = "fecha_ejecucion") private java.time.LocalDateTime fechaEjecucion;
    @Column(name = "fecha_registro", nullable = false) private LocalDate fechaRegistro;
    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fecha ASC, id ASC") private List<SolicitudHistorial> historialCambios = new ArrayList<>();

    public String nombreCompleto() { return (nombres + " " + apellidos).trim(); }
    public String ubicacion() { return latitud + ", " + longitud; }
}

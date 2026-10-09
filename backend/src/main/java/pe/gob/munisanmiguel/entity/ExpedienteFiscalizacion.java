package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name="expediente_fiscalizacion",uniqueConstraints=@UniqueConstraint(name="uk_expediente_solicitud_externa",columnNames={"dependencia_procedencia","referencia_solicitud"})) @Getter @Setter @NoArgsConstructor
public class ExpedienteFiscalizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,length=30) private String codigo;
    @Column(name="fecha_creacion",nullable=false) private LocalDateTime fechaCreacion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="creado_por_id") private AppUser creadoPor;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private OrigenFiscalizacion origen;
    @Column(name="detalle_origen",length=500) private String detalleOrigen;
    @Column(name="dependencia_procedencia",length=160) private String dependenciaProcedencia;
    @Column(name="referencia_solicitud",length=160) private String referenciaSolicitud;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private EstadoExpediente estado;
    @Column(name="objeto_fiscalizacion",nullable=false,length=255) private String objetoFiscalizacion;
    @Column(name="observaciones_iniciales",columnDefinition="TEXT") private String observacionesIniciales;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="solicitud_id",unique=true) private Solicitud solicitud;
    @Column(name="tipo_administrado",length=20) private String tipoAdministrado;
    @Column(name="documento_administrado",length=15) private String documentoAdministrado;
    @Column(name="nombre_administrado",length=280) private String nombreAdministrado;
    @Column(name="telefono_contacto",length=30) private String telefonoContacto;
    @Column(name="direccion_fiscalizada",length=250) private String direccionFiscalizada;
    @Column(name="administrado_apellidos",length=160) private String administradoApellidos;
    @Column(name="referencia_domicilio",length=250) private String referenciaDomicilio;
    @Column(precision=10,scale=7) private java.math.BigDecimal latitud;
    @Column(precision=10,scale=7) private java.math.BigDecimal longitud;
    @Column(name="documento_sustento_nombre",length=255) private String documentoSustentoNombre;
    @Column(name="documento_sustento_interno",length=120) private String documentoSustentoInterno;
    @Column(name="documento_sustento_tipo",length=80) private String documentoSustentoTipo;
    @Column(name="documento_sustento_fecha") private LocalDateTime documentoSustentoFecha;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false,insertable=false,updatable=false) private LocalDateTime updatedAt;
    @OneToMany(mappedBy="expediente",fetch=FetchType.LAZY) @OrderBy("fecha ASC, hora ASC, id ASC") private List<ProgramacionFiscalizacion> programaciones=new ArrayList<>();
}

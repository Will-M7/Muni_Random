package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name="diligencia_fiscalizacion") @Getter @Setter @NoArgsConstructor
public class DiligenciaFiscalizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="expediente_id",nullable=false) private ExpedienteFiscalizacion expediente;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="programacion_id",nullable=false,unique=true) private ProgramacionFiscalizacion programacion;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="fiscalizador_responsable_id",nullable=false) private Fiscalizador fiscalizadorResponsable;
    @Column(name="fecha_inicio",nullable=false) private LocalDateTime fechaInicio;
    @Column(name="fecha_cierre") private LocalDateTime fechaCierre;
    @Column(name="latitud_inicio",precision=10,scale=7) private BigDecimal latitudInicio;
    @Column(name="longitud_inicio",precision=10,scale=7) private BigDecimal longitudInicio;
    @Enumerated(EnumType.STRING) @Column(name="gps_inicio_estado",nullable=false,length=24) private EstadoGps gpsInicioEstado;
    @Column(name="latitud_cierre",precision=10,scale=7) private BigDecimal latitudCierre;
    @Column(name="longitud_cierre",precision=10,scale=7) private BigDecimal longitudCierre;
    @Enumerated(EnumType.STRING) @Column(name="gps_cierre_estado",nullable=false,length=24) private EstadoGps gpsCierreEstado;
    @Column(name="lugar_diligencia",nullable=false,length=250) private String lugarDiligencia;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private EstadoDiligencia estado;
    @Enumerated(EnumType.STRING) @Column(name="situacion_persona",nullable=false,length=32) private SituacionPersona situacionPersona;
    @Column(name="persona_nombres",length=160) private String personaNombres;
    @Column(name="persona_apellidos",length=160) private String personaApellidos;
    @Column(name="persona_tipo_documento",length=24) private String personaTipoDocumento;
    @Column(name="persona_numero_documento",length=24) private String personaNumeroDocumento;
    @Column(name="persona_condicion",length=32) private String personaCondicion;
    @Column(name="persona_observaciones",columnDefinition="TEXT") private String personaObservaciones;
    @Column(name="hechos_constatados",columnDefinition="TEXT") private String hechosConstatados;
    @Column(columnDefinition="TEXT") private String ocurrencias;
    @Column(name="observaciones_fiscalizador",columnDefinition="TEXT") private String observacionesFiscalizador;
    @Column(name="observaciones_fiscalizado",columnDefinition="TEXT") private String observacionesFiscalizado;
    @Enumerated(EnumType.STRING) @Column(name="motivo_no_realizada",length=40) private MotivoNoRealizada motivoNoRealizada;
    @Column(name="detalle_motivo_no_realizada",length=1000) private String detalleMotivoNoRealizada;
    @Column(name="copia_entregada",nullable=false) private boolean copiaEntregada;
    @Enumerated(EnumType.STRING) @Column(name="medio_entrega",nullable=false,length=24) private MedioEntrega medioEntrega;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
    @Column(name="registro_posterior",nullable=false) private boolean registroPosterior;
    @Column(name="registrado_en") private LocalDateTime registradoEn;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="registrado_por_id") private AppUser registradoPor;
    @Column(name="fecha_hora_ejecucion") private LocalDateTime fechaHoraEjecucion;
    @Enumerated(EnumType.STRING) @Column(name="origen_hora_ejecucion",length=20) private OrigenHoraEjecucion origenHoraEjecucion;
    @Version @Column(nullable=false) private Long version;
    @OneToMany(mappedBy="diligencia",fetch=FetchType.LAZY) @OrderBy("creadoEn ASC, id ASC") private List<DiligenciaParticipante> participantes=new ArrayList<>();
    @OneToMany(mappedBy="diligencia",fetch=FetchType.LAZY) @OrderBy("fechaHora ASC, id ASC") private List<EvidenciaFiscalizacion> evidencias=new ArrayList<>();
    @OneToOne(mappedBy="diligencia",fetch=FetchType.LAZY) private ActaFiscalizacion acta;
}

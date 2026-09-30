package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity @Table(name="programacion_fiscalizacion") @Getter @Setter @NoArgsConstructor
public class ProgramacionFiscalizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="expediente_id",nullable=false) private ExpedienteFiscalizacion expediente;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="fiscalizador_id") private Fiscalizador fiscalizador;
    private LocalDate fecha;
    private LocalTime hora;
    @Enumerated(EnumType.STRING) @Column(name="tipo_visita",nullable=false,length=20) private TipoVisita tipoVisita;
    @Enumerated(EnumType.STRING) @Column(name="estado_programacion",nullable=false,length=20) private EstadoProgramacion estadoProgramacion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="creada_por_id") private AppUser creadaPor;
    @Column(name="fecha_creacion",nullable=false) private LocalDateTime fechaCreacion;
    @Column(name="motivo_reprogramacion",length=500) private String motivoReprogramacion;
    @Column(name="motivo_cancelacion",length=500) private String motivoCancelacion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="programacion_anterior_id") private ProgramacionFiscalizacion programacionAnterior;
    @Column(name="resultado_motivo",columnDefinition="TEXT") private String resultadoMotivo;
    @Column(name="legacy_solicitud_codigo",length=30) private String legacySolicitudCodigo;
    @Column(name="motivo_seguimiento",length=1000) private String motivoSeguimiento;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="revision_origen_id") private RevisionFiscalizacion revisionOrigen;
}

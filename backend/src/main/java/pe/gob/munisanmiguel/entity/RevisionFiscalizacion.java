package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="revision_fiscalizacion") @Getter @Setter @NoArgsConstructor
public class RevisionFiscalizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="expediente_id",nullable=false) private ExpedienteFiscalizacion expediente;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="diligencia_id",nullable=false,unique=true) private DiligenciaFiscalizacion diligencia;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="acta_id",nullable=false) private ActaFiscalizacion acta;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="revisor_id") private AppUser revisor;
    @Column(name="fecha_inicio_revision") private LocalDateTime fechaInicioRevision;
    @Column(name="fecha_final_revision") private LocalDateTime fechaFinalRevision;
    @Enumerated(EnumType.STRING) @Column(name="estado_revision",nullable=false,length=30) private EstadoRevision estadoRevision;
    @Column(name="observaciones_revision",columnDefinition="TEXT") private String observacionesRevision;
    @Enumerated(EnumType.STRING) @Column(length=60) private ConclusionFiscalizacion conclusion;
    @Column(name="fundamento_conclusion",columnDefinition="TEXT") private String fundamentoConclusion;
    @Column(name="requiere_seguimiento",nullable=false) private boolean requiereSeguimiento;
    @Column(name="descripcion_conclusion",columnDefinition="TEXT") private String descripcionConclusion;
    @Column(name="plazo_conclusion",length=120) private String plazoConclusion;
    @Column(name="requiere_derivacion",nullable=false) private boolean requiereDerivacion;
    @Column(name="denominacion_otra",length=180) private String denominacionOtra;
    @Column(name="referencia_documentos",columnDefinition="TEXT") private String referenciaDocumentos;
    @Column(name="informe_pdf_nombre") private String informePdfNombre;
    @Column(name="informe_pdf_interno",length=120) private String informePdfInterno;
    @Column(name="informe_checksum",length=64) private String informeChecksum;
    @Column(name="creado_en",nullable=false) private LocalDateTime creadoEn;
    @Column(name="actualizado_en",nullable=false) private LocalDateTime actualizadoEn;
}

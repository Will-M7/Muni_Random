package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="acta_fiscalizacion") @Getter @Setter @NoArgsConstructor
public class ActaFiscalizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,length=30) private String codigo;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="diligencia_id",nullable=false,unique=true) private DiligenciaFiscalizacion diligencia;
    @Column(name="contenido_json",nullable=false,columnDefinition="json") private String contenidoJson;
    @Enumerated(EnumType.STRING) @Column(name="firma_fiscalizado_estado",nullable=false,length=32) private EstadoFirmaActa firmaFiscalizadoEstado;
    @Column(name="firma_fiscalizado_observacion",length=1000) private String firmaFiscalizadoObservacion;
    @Column(name="pdf_generado_nombre",nullable=false,length=255) private String pdfGeneradoNombre;
    @Column(name="pdf_generado_interno",nullable=false,unique=true,length=120) private String pdfGeneradoInterno;
    @Column(name="pdf_firmado_nombre",length=255) private String pdfFirmadoNombre;
    @Column(name="pdf_firmado_interno",unique=true,length=120) private String pdfFirmadoInterno;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="generado_por_id") private AppUser generadoPor;
    @Column(name="generado_en",nullable=false) private LocalDateTime generadoEn;
}

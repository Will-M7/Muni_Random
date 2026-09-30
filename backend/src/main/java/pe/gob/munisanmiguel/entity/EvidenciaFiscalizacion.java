package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="evidencia_fiscalizacion") @Getter @Setter @NoArgsConstructor
public class EvidenciaFiscalizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="diligencia_id",nullable=false) private DiligenciaFiscalizacion diligencia;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private TipoEvidencia tipo;
    @Column(name="nombre_original",nullable=false,length=255) private String nombreOriginal;
    @Column(name="nombre_interno",nullable=false,unique=true,length=120) private String nombreInterno;
    @Column(name="tipo_mime",nullable=false,length=100) private String tipoMime;
    @Column(name="tamano_bytes",nullable=false) private long tamanoBytes;
    @Column(length=500) private String descripcion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="creado_por_id") private AppUser creadoPor;
    @Column(name="fecha_hora",nullable=false) private LocalDateTime fechaHora;
}

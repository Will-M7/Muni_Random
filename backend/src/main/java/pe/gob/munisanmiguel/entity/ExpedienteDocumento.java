package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="expediente_documento") @Getter @Setter @NoArgsConstructor
public class ExpedienteDocumento {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="expediente_id",nullable=false) private ExpedienteFiscalizacion expediente;
    @Column(name="nombre_original",nullable=false,length=255) private String nombreOriginal;
    @Column(name="nombre_interno",nullable=false,unique=true,length=120) private String nombreInterno;
    @Column(name="tipo_mime",nullable=false,length=120) private String tipoMime;
    @Column(name="tamano_bytes",nullable=false) private long tamanoBytes;
    @Column(name="adjuntado_en",nullable=false) private LocalDateTime adjuntadoEn;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="adjuntado_por_id",nullable=false) private AppUser adjuntadoPor;
}

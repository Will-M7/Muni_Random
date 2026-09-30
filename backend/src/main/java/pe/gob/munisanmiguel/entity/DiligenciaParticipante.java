package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="diligencia_participante") @Getter @Setter @NoArgsConstructor
public class DiligenciaParticipante {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="diligencia_id",nullable=false) private DiligenciaFiscalizacion diligencia;
    @Enumerated(EnumType.STRING) @Column(name="tipo_participante",nullable=false,length=32) private TipoParticipante tipoParticipante;
    @Column(nullable=false,length=160) private String nombre;
    @Column(length=160) private String apellidos;
    @Column(name="tipo_documento",length=24) private String tipoDocumento;
    @Column(name="numero_documento",length=24) private String numeroDocumento;
    @Column(name="entidad_area",length=180) private String entidadArea;
    @Column(name="cargo_condicion",length=120) private String cargoCondicion;
    @Column(length=1000) private String observaciones;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="creado_por_id") private AppUser creadoPor;
    @Column(name="creado_en",nullable=false) private LocalDateTime creadoEn;
}

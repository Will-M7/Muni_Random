package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="solicitud_aclaracion_fiscalizacion") @Getter @Setter @NoArgsConstructor
public class SolicitudAclaracionFiscalizacion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="revision_id",nullable=false) private RevisionFiscalizacion revision;
    @Column(nullable=false,columnDefinition="TEXT") private String mensaje;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="solicitada_por_id",nullable=false) private AppUser solicitadaPor;
    @Column(name="fecha_solicitud",nullable=false) private LocalDateTime fechaSolicitud;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="respondida_por_id") private AppUser respondidaPor;
    @Column(name="fecha_respuesta") private LocalDateTime fechaRespuesta;
    @Column(columnDefinition="TEXT") private String respuesta;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private EstadoAclaracion estado;
    @Column(name="cerrada_en") private LocalDateTime cerradaEn;
}

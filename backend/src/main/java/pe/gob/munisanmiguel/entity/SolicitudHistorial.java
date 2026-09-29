package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity @Table(name = "solicitud_historial") @Getter @Setter @NoArgsConstructor
public class SolicitudHistorial {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "solicitud_id", nullable = false) private Solicitud solicitud;
    @Column(nullable = false) private LocalDate fecha;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private EstadoSolicitud estado;
    @Column(length = 500) private String nota;
}

package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="expediente_historial") @Getter @Setter @NoArgsConstructor
public class ExpedienteHistorial {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="expediente_id",nullable=false) private ExpedienteFiscalizacion expediente;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="programacion_id") private ProgramacionFiscalizacion programacion;
    @Column(name="fecha_hora",nullable=false) private LocalDateTime fechaHora;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="actor_id") private AppUser actor;
    @Column(name="actor_username",length=120) private String actorUsername;
    @Column(name="tipo_evento",nullable=false,length=40) private String tipoEvento;
    @Column(nullable=false,length=1000) private String descripcion;
    @Column(name="valores_anteriores",columnDefinition="TEXT") private String valoresAnteriores;
    @Column(name="valores_nuevos",columnDefinition="TEXT") private String valoresNuevos;
}

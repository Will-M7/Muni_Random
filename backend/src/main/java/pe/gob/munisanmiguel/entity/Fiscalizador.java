package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "fiscalizador") @Getter @Setter @NoArgsConstructor
public class Fiscalizador {
    @Id @Column(length = 20) private String id;
    @Column(nullable = false, unique = true, length = 20) private String codigo;
    @Column(nullable = false, length = 160) private String nombre;
    @Column(nullable = false, length = 180) private String zona;
    @Column(nullable = false, length = 30) private String telefono;
    @Column(nullable = false) private boolean activo;
}

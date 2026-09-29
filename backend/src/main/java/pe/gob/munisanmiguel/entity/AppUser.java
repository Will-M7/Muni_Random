package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "app_user") @Getter @Setter @NoArgsConstructor
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 120) private String username;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Column(name = "display_name", nullable = false, length = 160) private String displayName;
    @Column(nullable = false, length = 180) private String cargo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Rol role;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "fiscalizador_id") private Fiscalizador fiscalizador;
    @Column(nullable = false) private boolean activo;
    @Column(name = "created_at", nullable = false, updatable = false) private java.time.LocalDateTime createdAt;
}

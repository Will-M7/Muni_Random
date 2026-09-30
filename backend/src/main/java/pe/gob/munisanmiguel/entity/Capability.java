package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "capability") @Getter @Setter @NoArgsConstructor
public class Capability {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 80) private String code;
    @Column(nullable = false, length = 200) private String description;
    @Column(nullable = false, length = 80) private String category;
}

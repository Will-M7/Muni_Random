package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity @Table(name = "app_role") @Getter @Setter @NoArgsConstructor
public class AppRole {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 60) private String name;
    @Column(nullable = false, length = 200) private String description;
    @Column(nullable = false) private boolean configurable;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_capability", joinColumns = @JoinColumn(name = "role_id"), inverseJoinColumns = @JoinColumn(name = "capability_id"))
    @OrderBy("code ASC") private Set<Capability> capabilities = new LinkedHashSet<>();
}

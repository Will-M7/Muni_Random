package pe.gob.munisanmiguel.repository;
import pe.gob.munisanmiguel.entity.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface AppRoleRepository extends JpaRepository<AppRole, Long> {
    Optional<AppRole> findByName(String name);
}

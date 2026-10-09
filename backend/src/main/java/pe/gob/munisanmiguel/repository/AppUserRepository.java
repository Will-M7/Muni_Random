package pe.gob.munisanmiguel.repository;
import pe.gob.munisanmiguel.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsernameIgnoreCaseAndActivoTrue(String username);
    Optional<AppUser> findByUsernameIgnoreCase(String username);
    List<AppUser> findAllByOrderByUsernameAsc();
    boolean existsByFiscalizadorId(String fiscalizadorId);
}

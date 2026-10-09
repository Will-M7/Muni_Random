package pe.gob.munisanmiguel.repository;
import pe.gob.munisanmiguel.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select distinct u from AppUser u join u.roles r where r.name = 'ADMIN_SISTEMA' and u.activo = true")
    List<AppUser> activeAdminsForUpdate();
    Optional<AppUser> findByUsernameIgnoreCaseAndActivoTrue(String username);
    Optional<AppUser> findByUsernameIgnoreCase(String username);
    List<AppUser> findAllByOrderByUsernameAsc();
    boolean existsByFiscalizadorId(String fiscalizadorId);
    Optional<AppUser> findByFiscalizadorId(String fiscalizadorId);
}

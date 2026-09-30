package pe.gob.munisanmiguel.repository;
import pe.gob.munisanmiguel.entity.Capability;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CapabilityRepository extends JpaRepository<Capability, Long> {
    Optional<Capability> findByCode(String code);
}

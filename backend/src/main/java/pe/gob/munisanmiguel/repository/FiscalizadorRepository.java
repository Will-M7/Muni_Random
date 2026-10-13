package pe.gob.munisanmiguel.repository;
import pe.gob.munisanmiguel.entity.Fiscalizador;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FiscalizadorRepository extends JpaRepository<Fiscalizador, String> {
    boolean existsByMunicipioResponsableId(Long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select f from Fiscalizador f where f.id = :id")
    java.util.Optional<Fiscalizador> findForIdentityLink(@org.springframework.data.repository.query.Param("id") String id);
}

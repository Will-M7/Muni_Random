package pe.gob.munisanmiguel.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import pe.gob.munisanmiguel.entity.*;
import java.util.*;
public interface RevisionFiscalizacionRepository extends JpaRepository<RevisionFiscalizacion,Long> {
 Optional<RevisionFiscalizacion> findByDiligenciaId(Long id);
 List<RevisionFiscalizacion> findAllByExpedienteCodigoOrderByCreadoEnAsc(String codigo);
 List<RevisionFiscalizacion> findAllByEstadoRevisionInOrderByFechaInicioRevisionAsc(List<EstadoRevision> estados);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from RevisionFiscalizacion r where r.id=:id") Optional<RevisionFiscalizacion> findLocked(@Param("id") Long id);
}

package pe.gob.munisanmiguel.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.gob.munisanmiguel.entity.DiligenciaFiscalizacion;
import pe.gob.munisanmiguel.entity.EstadoDiligencia;
import java.util.List;
import java.util.Optional;

public interface DiligenciaFiscalizacionRepository extends JpaRepository<DiligenciaFiscalizacion,Long> {
    Optional<DiligenciaFiscalizacion> findByProgramacionId(Long programacionId);
    List<DiligenciaFiscalizacion> findAllByExpedienteIdOrderByFechaInicioDesc(Long expedienteId);
    List<DiligenciaFiscalizacion> findAllByEstadoOrderByFechaCierreAsc(EstadoDiligencia estado);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DiligenciaFiscalizacion d where d.id = :id")
    Optional<DiligenciaFiscalizacion> findByIdForUpdate(@Param("id") Long id);
}

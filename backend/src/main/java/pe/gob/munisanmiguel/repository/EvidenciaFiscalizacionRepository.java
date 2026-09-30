package pe.gob.munisanmiguel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.gob.munisanmiguel.entity.EvidenciaFiscalizacion;
import java.util.List;
import java.util.Optional;

public interface EvidenciaFiscalizacionRepository extends JpaRepository<EvidenciaFiscalizacion,Long> {
    List<EvidenciaFiscalizacion> findAllByDiligenciaIdOrderByFechaHoraAscIdAsc(Long diligenciaId);
    Optional<EvidenciaFiscalizacion> findByIdAndDiligenciaId(Long id,Long diligenciaId);
}

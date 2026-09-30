package pe.gob.munisanmiguel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.gob.munisanmiguel.entity.ActaFiscalizacion;
import java.util.List;
import java.util.Optional;

public interface ActaFiscalizacionRepository extends JpaRepository<ActaFiscalizacion,Long> {
    Optional<ActaFiscalizacion> findByDiligenciaId(Long diligenciaId);
    List<ActaFiscalizacion> findAllByDiligenciaExpedienteCodigoOrderByGeneradoEnAsc(String codigo);
}

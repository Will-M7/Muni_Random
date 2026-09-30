package pe.gob.munisanmiguel.repository;

import pe.gob.munisanmiguel.entity.ExpedienteHistorial;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpedienteHistorialRepository extends JpaRepository<ExpedienteHistorial,Long> {
    List<ExpedienteHistorial> findAllByExpedienteIdOrderByFechaHoraAscIdAsc(Long expedienteId);
}

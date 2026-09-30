package pe.gob.munisanmiguel.repository;

import pe.gob.munisanmiguel.entity.ExpedienteFiscalizacion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ExpedienteFiscalizacionRepository extends JpaRepository<ExpedienteFiscalizacion,Long> {
    @EntityGraph(attributePaths={"solicitud","creadoPor","programaciones","programaciones.fiscalizador"})
    List<ExpedienteFiscalizacion> findAllByOrderByFechaCreacionDescIdDesc();
    @EntityGraph(attributePaths={"solicitud","creadoPor","programaciones","programaciones.fiscalizador"})
    Optional<ExpedienteFiscalizacion> findByCodigo(String codigo);
    Optional<ExpedienteFiscalizacion> findBySolicitudId(Long solicitudId);
}

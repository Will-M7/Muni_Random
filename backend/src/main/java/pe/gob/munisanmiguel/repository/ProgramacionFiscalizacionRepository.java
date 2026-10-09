package pe.gob.munisanmiguel.repository;

import pe.gob.munisanmiguel.entity.EstadoProgramacion;
import pe.gob.munisanmiguel.entity.ProgramacionFiscalizacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ProgramacionFiscalizacionRepository extends JpaRepository<ProgramacionFiscalizacion,Long> {
    List<ProgramacionFiscalizacion> findAllByOrderByFechaAscHoraAsc();
    List<ProgramacionFiscalizacion> findAllByFechaOrderByHoraAscIdAsc(LocalDate fecha);
    List<ProgramacionFiscalizacion> findAllByExpedienteIdOrderByIdAsc(Long expedienteId);
    List<ProgramacionFiscalizacion> findAllByFiscalizadorIdAndEstadoProgramacionInOrderByFechaAscHoraAsc(String fiscalizadorId,List<EstadoProgramacion> estados);
    List<ProgramacionFiscalizacion> findAllByFiscalizadorIdOrderByIdAsc(String fiscalizadorId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProgramacionFiscalizacion p where p.id = :id")
    java.util.Optional<ProgramacionFiscalizacion> findByIdForUpdate(@Param("id") Long id);
    boolean existsByFiscalizadorIdAndFechaAndHoraAndEstadoProgramacionIn(String fiscalizadorId,LocalDate fecha,LocalTime hora,List<EstadoProgramacion> estados);
    List<ProgramacionFiscalizacion> findAllByRevisionOrigen_IdOrderByIdAsc(Long revisionId);
    boolean existsByRevisionOrigen_Id(Long revisionId);
}

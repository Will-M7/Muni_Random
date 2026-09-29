package pe.gob.munisanmiguel.repository;

import pe.gob.munisanmiguel.entity.Solicitud;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {
    Optional<Solicitud> findByCodigo(String codigo);
    List<Solicitud> findAllByOrderByFechaRegistroDescIdDesc();
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Solicitud s where s.estado in :estados and s.resultadoFiscalizacion = :resultado and s.fechaFiscalizacion is not null and s.horaFiscalizacion is not null and (s.fechaFiscalizacion < :fecha or (s.fechaFiscalizacion = :fecha and s.horaFiscalizacion < :hora))")
    List<Solicitud> vencidasPendientes(@Param("estados") List<pe.gob.munisanmiguel.entity.EstadoSolicitud> estados,
        @Param("resultado") pe.gob.munisanmiguel.entity.ResultadoFiscalizacion resultado,
        @Param("fecha") LocalDate fecha, @Param("hora") LocalTime hora);
    @Query("select s from Solicitud s left join s.fiscalizador f where (:fecha is null or s.fecha = :fecha) and (:estado is null or s.estado = :estado) and (:fiscalizadorId is null or f.id = :fiscalizadorId) and (:q is null or lower(s.codigo) like lower(concat('%', :q, '%')) or lower(concat(s.nombres, ' ', s.apellidos)) like lower(concat('%', :q, '%')) or s.dni like concat('%', :q, '%') or lower(s.direccion) like lower(concat('%', :q, '%')) or lower(coalesce(f.nombre,'')) like lower(concat('%', :q, '%'))) order by s.fecha asc, s.hora asc")
    List<Solicitud> buscar(@Param("fecha") LocalDate fecha, @Param("estado") pe.gob.munisanmiguel.entity.EstadoSolicitud estado,
                           @Param("fiscalizadorId") String fiscalizadorId, @Param("q") String q);
    List<Solicitud> findAllByFechaFiscalizacionAndHoraFiscalizacionIsNotNullOrderByHoraFiscalizacionAsc(LocalDate fechaFiscalizacion);
    boolean existsByFechaFiscalizacionAndHoraFiscalizacion(LocalDate fechaFiscalizacion, java.time.LocalTime horaFiscalizacion);
    boolean existsByFechaFiscalizacionAndHoraFiscalizacionAndIdNot(LocalDate fechaFiscalizacion, java.time.LocalTime horaFiscalizacion, Long id);
    boolean existsByFechaFiscalizacionAndHoraFiscalizacionAndFiscalizadorId(LocalDate fechaFiscalizacion, java.time.LocalTime horaFiscalizacion, String fiscalizadorId);
    boolean existsByFechaFiscalizacionAndHoraFiscalizacionAndFiscalizadorIdAndIdNot(LocalDate fechaFiscalizacion, java.time.LocalTime horaFiscalizacion, String fiscalizadorId, Long id);
    List<Solicitud> findAllByFiscalizadorIdAndFechaFiscalizacionOrderByHoraFiscalizacionAsc(String fiscalizadorId, LocalDate fechaFiscalizacion);
    List<Solicitud> findAllByFiscalizadorIdAndFechaFiscalizacionGreaterThanOrderByFechaFiscalizacionAscHoraFiscalizacionAsc(String fiscalizadorId, LocalDate fechaFiscalizacion);
    List<Solicitud> findAllByFiscalizadorIdAndResultadoFiscalizacionInOrderByFechaFiscalizacionDescHoraFiscalizacionDesc(String fiscalizadorId, List<pe.gob.munisanmiguel.entity.ResultadoFiscalizacion> resultados);
}

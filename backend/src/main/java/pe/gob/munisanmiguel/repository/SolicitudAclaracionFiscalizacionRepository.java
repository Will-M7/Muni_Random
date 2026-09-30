package pe.gob.munisanmiguel.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.gob.munisanmiguel.entity.*;
import java.util.*;
public interface SolicitudAclaracionFiscalizacionRepository extends JpaRepository<SolicitudAclaracionFiscalizacion,Long> {
 List<SolicitudAclaracionFiscalizacion> findAllByRevisionIdOrderByFechaSolicitudAsc(Long revisionId);
 List<SolicitudAclaracionFiscalizacion> findAllByEstadoAndRevisionDiligenciaFiscalizadorResponsableIdOrderByFechaSolicitudAsc(EstadoAclaracion estado,String fiscalizadorId);
 boolean existsByRevisionExpedienteIdAndEstado(Long expedienteId,EstadoAclaracion estado);
 boolean existsByRevisionIdAndEstado(Long revisionId,EstadoAclaracion estado);
}

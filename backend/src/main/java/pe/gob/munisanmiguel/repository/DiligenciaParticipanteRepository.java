package pe.gob.munisanmiguel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.gob.munisanmiguel.entity.DiligenciaParticipante;
import java.util.List;

public interface DiligenciaParticipanteRepository extends JpaRepository<DiligenciaParticipante,Long> {
    List<DiligenciaParticipante> findAllByDiligenciaIdOrderByCreadoEnAscIdAsc(Long diligenciaId);
}

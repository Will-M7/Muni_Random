package pe.gob.munisanmiguel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.gob.munisanmiguel.entity.ExpedienteDocumento;
import java.util.List;
import java.util.Optional;

public interface ExpedienteDocumentoRepository extends JpaRepository<ExpedienteDocumento,Long> {
    List<ExpedienteDocumento> findAllByExpedienteCodigoOrderByAdjuntadoEnAscIdAsc(String codigo);
    Optional<ExpedienteDocumento> findByIdAndExpedienteCodigo(Long id,String codigo);
}

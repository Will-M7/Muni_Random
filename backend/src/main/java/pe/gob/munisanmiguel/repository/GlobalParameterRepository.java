package pe.gob.munisanmiguel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.gob.munisanmiguel.entity.GlobalParameter;

public interface GlobalParameterRepository extends JpaRepository<GlobalParameter,String> {}

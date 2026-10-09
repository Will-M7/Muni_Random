package pe.gob.munisanmiguel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.gob.munisanmiguel.entity.UserPreference;

public interface UserPreferenceRepository extends JpaRepository<UserPreference,Long> {}

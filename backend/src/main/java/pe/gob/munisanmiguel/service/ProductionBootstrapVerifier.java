package pe.gob.munisanmiguel.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Verify and mark the first clean production bootstrap after the admin runner. */
@Component @Profile("prod") @Order(1)
public class ProductionBootstrapVerifier implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final String adminUsername;

    public ProductionBootstrapVerifier(JdbcTemplate jdbc,@Value("${app.bootstrap-admin.username}") String adminUsername){
        this.jdbc=jdbc;this.adminUsername=adminUsername;
    }

    @Override public void run(ApplicationArguments args){
        Integer bootstrapped=jdbc.queryForObject("SELECT bootstrapped FROM production_seed_guard WHERE id=1 AND verified=1",Integer.class);
        if(bootstrapped==null)throw new IllegalStateException("Falta la verificación de base nueva de producción.");
        if(bootstrapped==0){
            Integer admins=jdbc.queryForObject("SELECT COUNT(*) FROM app_user u JOIN user_role ur ON ur.user_id=u.id JOIN app_role r ON r.id=ur.role_id WHERE u.username=? AND u.role='ADMIN_SISTEMA' AND r.name='ADMIN_SISTEMA' AND u.activo=1",Integer.class,adminUsername.trim());
            Integer users=jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Integer.class);
            Integer operational=jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM fiscalizador)+(SELECT COUNT(*) FROM solicitud)+(SELECT COUNT(*) FROM expediente_fiscalizacion)+(SELECT COUNT(*) FROM programacion_fiscalizacion)+(SELECT COUNT(*) FROM diligencia_fiscalizacion)+(SELECT COUNT(*) FROM acta_fiscalizacion)+(SELECT COUNT(*) FROM expediente_documento)+(SELECT COUNT(*) FROM evidencia_fiscalizacion)+(SELECT COUNT(*) FROM revision_fiscalizacion)",Integer.class);
            if(admins==null||admins!=1||users==null||users!=1||operational==null||operational!=0)
                throw new IllegalStateException("La base nueva no contiene exclusivamente al administrador y los catálogos.");
            jdbc.update("UPDATE production_seed_guard SET bootstrapped=1 WHERE id=1 AND bootstrapped=0");
        }
    }
}

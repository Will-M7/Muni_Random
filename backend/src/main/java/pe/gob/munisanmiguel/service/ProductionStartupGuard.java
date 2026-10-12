package pe.gob.munisanmiguel.service;

import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Validate production secrets before BootstrapAdmin can create an account. */
@Component @Profile("prod") @Order(-1)
public class ProductionStartupGuard implements ApplicationRunner {
    private final String jwtSecret, masterKey, adminUsername, adminPassword, databasePassword, corsOrigins;

    public ProductionStartupGuard(@Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.identity.master-key}") String masterKey,
            @Value("${app.bootstrap-admin.username}") String adminUsername,
            @Value("${app.bootstrap-admin.password}") String adminPassword,
            @Value("${spring.datasource.password}") String databasePassword,
            @Value("${app.cors-origins}") String corsOrigins) {
        this.jwtSecret=jwtSecret;this.masterKey=masterKey;
        this.adminUsername=adminUsername;this.adminPassword=adminPassword;
        this.databasePassword=databasePassword;this.corsOrigins=corsOrigins;
    }

    @Override public void run(ApplicationArguments args) {
        if(jwtSecret.length()<48||jwtSecret.startsWith("change-this-secret")
                ||adminUsername.isBlank()||adminPassword.length()<16||databasePassword.length()<16
                ||!corsOrigins.startsWith("https://")||corsOrigins.contains("localhost:4200"))
            throw new IllegalStateException("Configuración de seguridad de producción incompleta o débil.");
        try {if(Base64.getDecoder().decode(masterKey).length!=32)throw new IllegalArgumentException();}
        catch(IllegalArgumentException e){throw new IllegalStateException("IDENTITY_MASTER_KEY de producción inválida.");}

    }
}

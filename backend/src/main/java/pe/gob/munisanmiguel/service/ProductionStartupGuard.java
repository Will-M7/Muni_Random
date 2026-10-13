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
                ||!validOrigins(corsOrigins))
            throw new IllegalStateException("Configuración de seguridad de producción incompleta o débil.");
        try {if(Base64.getDecoder().decode(masterKey).length!=32)throw new IllegalArgumentException();}
        catch(IllegalArgumentException e){throw new IllegalStateException("IDENTITY_MASTER_KEY de producción inválida.");}

    }

    private static boolean validOrigins(String origins) {
        if(origins==null||origins.isBlank())return false;
        for(String value:origins.split(",",-1)) {
            try {
                var uri=java.net.URI.create(value.trim());
                if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null
                        ||uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null
                        ||(uri.getRawPath()!=null&&!uri.getRawPath().isEmpty())
                        ||uri.getPort()==0||uri.getPort()>65535)return false;
            } catch(IllegalArgumentException e) { return false; }
        }
        return true;
    }
}

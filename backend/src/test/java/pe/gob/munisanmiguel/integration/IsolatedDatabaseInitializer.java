package pe.gob.munisanmiguel.integration;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Rejects live/local municipal databases before Flyway or JPA can connect. */
public class IsolatedDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override public void initialize(ConfigurableApplicationContext context) {
        requireIsolatedUrl(context.getEnvironment().getProperty("municipal.test.jdbc-url"));
    }
    public static void requireIsolatedUrl(String url) {
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:13317/v20_[a-z0-9_]+(?:\\?.*)?"))
            throw new IllegalArgumentException("Las pruebas requieren municipal.test.jdbc-url en 127.0.0.1:13317/v20_*. No se permite san_miguel ni el puerto 3307.");
    }
}

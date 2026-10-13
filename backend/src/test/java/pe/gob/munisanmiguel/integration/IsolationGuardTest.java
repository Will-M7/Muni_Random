package pe.gob.munisanmiguel.integration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IsolationGuardTest {
    @Test void rechazaBaseOriginalProduccionYPuertoLocal(){
        for(String url:new String[]{"jdbc:mysql://localhost:3307/san_miguel","jdbc:mysql://127.0.0.1:13317/san_miguel","jdbc:mysql://production:3306/v20_test","jdbc:mysql://127.0.0.1:3307/v20_test"})
            assertThrows(IllegalArgumentException.class,()->IsolatedDatabaseInitializer.requireIsolatedUrl(url));
        assertThrows(IllegalArgumentException.class,()->IsolatedDatabaseInitializer.requireIsolatedUrl(null));
        assertDoesNotThrow(()->IsolatedDatabaseInitializer.requireIsolatedUrl("jdbc:mysql://127.0.0.1:13317/v20_clean?allowPublicKeyRetrieval=true"));
    }
}

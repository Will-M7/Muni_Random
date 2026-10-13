package pe.gob.munisanmiguel.service;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductionStartupGuardTest {
    private ProductionStartupGuard guard(String origins) {
        return new ProductionStartupGuard("s".repeat(64), Base64.getEncoder().encodeToString(new byte[32]),
                "qa-admin", "p".repeat(24), "d".repeat(24), origins);
    }
    @Test void acceptsExplicitHttpsOriginsForDesktopAndMobile() {
        assertDoesNotThrow(()->guard("https://municipio.example,https://192.168.1.67:18443").run(null));
    }
    @Test void rejectsAnHttpOriginHiddenAfterTheFirstHttpsOrigin() {
        assertThrows(IllegalStateException.class,()->guard("https://municipio.example,http://insecure.example").run(null));
    }
    @Test void rejectsWildcardsPathsCredentialsAndEmptyEntries() {
        for(String origins:new String[]{"https://*", "https://municipio.example/path", "https://user@municipio.example",
                "https://municipio.example,", "https://municipio.example?token=private", "https://municipio.example#fragment"})
            assertThrows(IllegalStateException.class,()->guard(origins).run(null),origins);
    }
    @Test void rejectsAnInvalidEncryptionKey() {
        var guard=new ProductionStartupGuard("s".repeat(64),"invalid", "qa-admin", "p".repeat(24), "d".repeat(24), "https://municipio.example");
        assertThrows(IllegalStateException.class,()->guard.run(null));
    }
}

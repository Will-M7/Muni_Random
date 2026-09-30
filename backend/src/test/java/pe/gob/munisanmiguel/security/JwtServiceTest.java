package pe.gob.munisanmiguel.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {
    private static final String SECRET = "san-miguel-test-secret-with-at-least-32-bytes";

    @Test
    void creaYValidaClaimsDelToken() {
        JwtService service = new JwtService(SECRET, 15);
        String token = service.create("municipio", null);

        var claims = service.parse(token).getPayload();
        assertEquals("municipio", claims.getSubject());
        org.junit.jupiter.api.Assertions.assertFalse(claims.containsKey("role"));
        assertEquals("", claims.get("fiscalizadorId", String.class));
    }

    @Test
    void rechazaTokenAlterado() {
        JwtService service = new JwtService(SECRET, 15);
        String token = service.create("municipio", null);

        assertThrows(RuntimeException.class, () -> service.parse(token + "x"));
    }
}

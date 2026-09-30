package pe.gob.munisanmiguel.service;

import org.junit.jupiter.api.Test;
import pe.gob.munisanmiguel.dto.ApiDtos.LoginRequest;
import pe.gob.munisanmiguel.entity.AppRole;
import pe.gob.munisanmiguel.entity.AppUser;
import pe.gob.munisanmiguel.entity.Capability;
import pe.gob.munisanmiguel.entity.Rol;
import pe.gob.munisanmiguel.repository.AppUserRepository;
import pe.gob.munisanmiguel.security.JwtService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtService jwt = mock(JwtService.class);
    private final AuthService service = new AuthService(users, encoder, jwt);

    @Test void loginReturnsEffectiveMunicipioCapabilities() {
        assertLogin("MUNICIPIO", Rol.ADMIN_SISTEMA, "municipio2026", "$2b$10$nkT/BPxmnZe2ClwQQmCKfu3qi13WS77tvdl2GUzZ3H88SnLw0Jyh6", "SOLICITUD_VER", "SOLICITUD_ESTADO_CAMBIAR");
    }

    @Test void loginReturnsEffectiveFiscalizadorCapabilities() {
        assertLogin("FISCALIZADOR", Rol.FISCALIZADOR, "fiscal2026", "$2b$10$EwZnxG1xqhjnlAFZNykLUe8cM4pObuWDXkUX/bZkaY9gWGq0rdW1u", "TAREAS_PROPIAS_VER", "RESULTADO_REGISTRAR");
    }

    @Test void loginReturnsAdminWithoutOperationalCapabilities() {
        var response=assertLogin("ADMIN_SISTEMA", Rol.ADMIN_SISTEMA, "test-secret-admin", encoder.encode("test-secret-admin"), "USUARIOS_VER", "ROLES_GESTIONAR");
        org.junit.jupiter.api.Assertions.assertFalse(response.capacidades().contains("FISCALIZACION_EJECUTAR"));
    }

    private pe.gob.munisanmiguel.dto.ApiDtos.LoginResponse assertLogin(String roleName, Rol legacyRole, String rawPassword, String hash, String... codes) {
        var role=new AppRole(); role.setName(roleName);
        for (String code : codes) { var capability=new Capability(); capability.setCode(code); role.getCapabilities().add(capability); }
        var user=new AppUser(); user.setUsername("user"); user.setPasswordHash(hash); user.setRole(legacyRole); user.setRoles(Set.of(role)); user.setDisplayName("Usuario"); user.setCargo(roleName);
        when(users.findByUsernameIgnoreCaseAndActivoTrue("user")).thenReturn(java.util.Optional.of(user));
        when(jwt.create(anyString(), anyString())).thenReturn("signed-token");
        var response=service.login(new LoginRequest("user", null, rawPassword, null));
        assertEquals(roleName, response.rol());
        assertEquals(java.util.List.of(roleName), response.roles());
        assertEquals(java.util.Arrays.stream(codes).sorted().toList(), response.capacidades());
        verify(jwt).create("user", "");
        return response;
    }
}

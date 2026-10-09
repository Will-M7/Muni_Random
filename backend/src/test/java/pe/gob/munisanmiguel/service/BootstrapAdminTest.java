package pe.gob.munisanmiguel.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.gob.munisanmiguel.entity.AppRole;
import pe.gob.munisanmiguel.entity.AppUser;
import pe.gob.munisanmiguel.repository.*;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BootstrapAdminTest {
    @Test void aceptaOchoCaracteresYGuardaPreferencias(){
        var users=mock(AppUserRepository.class);var roles=mock(AppRoleRepository.class);var settings=mock(AdminSettingsService.class);
        var role=new AppRole();role.setName("ADMIN_SISTEMA");when(roles.findByName("ADMIN_SISTEMA")).thenReturn(Optional.of(role));
        when(users.saveAndFlush(any())).thenAnswer(i->{AppUser u=i.getArgument(0);u.setId(1L);return u;});
        new BootstrapAdmin(users,roles,mock(PasswordEncoder.class),settings,"bootstrap","12345678").run(mock(ApplicationArguments.class));
        verify(users).saveAndFlush(any(AppUser.class));verify(settings).createDefaults(any(AppUser.class));
    }
}

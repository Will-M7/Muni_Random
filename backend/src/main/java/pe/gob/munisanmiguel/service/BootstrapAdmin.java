package pe.gob.munisanmiguel.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.gob.munisanmiguel.entity.AppUser;
import pe.gob.munisanmiguel.entity.Rol;
import pe.gob.munisanmiguel.repository.AppRoleRepository;
import pe.gob.munisanmiguel.repository.AppUserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class BootstrapAdmin implements ApplicationRunner {
    private final AppUserRepository users;
    private final AppRoleRepository roles;
    private final PasswordEncoder encoder;
    private final AdminSettingsService settings;
    private final String username;
    private final String password;

    public BootstrapAdmin(AppUserRepository users, AppRoleRepository roles, PasswordEncoder encoder,AdminSettingsService settings,
                          @Value("${app.bootstrap-admin.username:}") String username,
                          @Value("${app.bootstrap-admin.password:}") String password) {
        this.users=users; this.roles=roles; this.encoder=encoder;this.settings=settings; this.username=username; this.password=password;
    }

    @Override @Transactional public void run(ApplicationArguments args) {
        if (username.isBlank() || password.length() < 8 || users.findByUsernameIgnoreCase(username.trim()).isPresent()) return;
        var user=new AppUser(); user.setUsername(username.trim()); user.setPasswordHash(encoder.encode(password));
        user.setDisplayName("Administrador del sistema"); user.setCargo("Administración del sistema");
        user.setRole(Rol.ADMIN_SISTEMA); user.getRoles().add(roles.findByName("ADMIN_SISTEMA").orElseThrow());
        user.setActivo(true); user.setCreatedAt(LocalDateTime.now(ZoneId.of("America/Lima"))); users.saveAndFlush(user);settings.createDefaults(user);
    }
}

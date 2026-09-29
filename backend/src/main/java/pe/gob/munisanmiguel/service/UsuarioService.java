package pe.gob.munisanmiguel.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.gob.munisanmiguel.dto.ApiDtos.UsuarioRequest;
import pe.gob.munisanmiguel.dto.ApiDtos.UsuarioResponse;
import pe.gob.munisanmiguel.entity.AppUser;
import pe.gob.munisanmiguel.exception.ConflictException;
import pe.gob.munisanmiguel.repository.AppUserRepository;
import pe.gob.munisanmiguel.dto.ApiDtos.FiscalizadorResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class UsuarioService {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    public UsuarioService(AppUserRepository users, PasswordEncoder encoder) { this.users = users; this.encoder = encoder; }
    @Transactional(readOnly = true) public java.util.List<UsuarioResponse> listar() { return users.findAllByOrderByUsernameAsc().stream().map(this::toResponse).toList(); }
    @Transactional public UsuarioResponse crear(UsuarioRequest request) {
        if (!request.password().equals(request.confirmarPassword())) throw new IllegalArgumentException("Las contraseñas no coinciden.");
        if (users.findByUsernameIgnoreCase(request.username().trim()).isPresent()) throw new ConflictException("El nombre de usuario ya existe.");
        var user = new AppUser(); user.setUsername(request.username().trim()); user.setDisplayName(request.nombre().trim()); user.setCargo(request.rol().name().equals("MUNICIPIO") ? "Gestión municipal" : "Fiscalizador Técnico de Campo"); user.setPasswordHash(encoder.encode(request.password())); user.setRole(request.rol()); user.setActivo(true); user.setCreatedAt(LocalDateTime.now(ZoneId.of("America/Lima")));
        return toResponse(users.save(user));
    }
    @Transactional(readOnly = true) public java.util.List<FiscalizadorResponse> fiscalizadores() {
        return users.findAllByOrderByUsernameAsc().stream()
                .filter(u -> u.isActivo() && u.getRole() == pe.gob.munisanmiguel.entity.Rol.FISCALIZADOR && u.getFiscalizador() != null && u.getFiscalizador().isActivo())
                .map(u -> { var f=u.getFiscalizador(); return new FiscalizadorResponse(f.getId(), f.getCodigo(), f.getNombre(), f.getZona(), f.getTelefono(), f.isActivo()); })
                .toList();
    }
    private UsuarioResponse toResponse(AppUser user) { return new UsuarioResponse(user.getUsername(), user.getDisplayName(), user.getRole().name(), user.isActivo(), user.getCreatedAt() == null ? LocalDate.now().toString() : user.getCreatedAt().toLocalDate().toString()); }
}

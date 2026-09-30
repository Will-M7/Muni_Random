package pe.gob.munisanmiguel.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.gob.munisanmiguel.dto.ApiDtos.UsuarioRequest;
import pe.gob.munisanmiguel.dto.ApiDtos.UsuarioResponse;
import pe.gob.munisanmiguel.entity.AppUser;
import pe.gob.munisanmiguel.exception.ConflictException;
import pe.gob.munisanmiguel.repository.AppUserRepository;
import pe.gob.munisanmiguel.repository.AppRoleRepository;
import pe.gob.munisanmiguel.entity.AppRole;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.gob.munisanmiguel.dto.ApiDtos.FiscalizadorDisponibleResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class UsuarioService {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AppRoleRepository roles;
    public UsuarioService(AppUserRepository users, PasswordEncoder encoder, AppRoleRepository roles) { this.users = users; this.encoder = encoder; this.roles = roles; }
    @Transactional(readOnly = true) public java.util.List<UsuarioResponse> listar() { return users.findAllByOrderByUsernameAsc().stream().map(this::toResponse).toList(); }
    @Transactional public UsuarioResponse crear(UsuarioRequest request) {
        if (!canManageRoles() && request.rol() == pe.gob.munisanmiguel.entity.Rol.ADMIN_SISTEMA) throw new org.springframework.security.access.AccessDeniedException("No puedes asignar ADMIN_SISTEMA.");
        if (!request.password().equals(request.confirmarPassword())) throw new IllegalArgumentException("Las contraseñas no coinciden.");
        if (users.findByUsernameIgnoreCase(request.username().trim()).isPresent()) throw new ConflictException("El nombre de usuario ya existe.");
        var role = roles.findByName(request.rol().name()).orElseThrow();
        var user = new AppUser(); user.setUsername(request.username().trim()); user.setDisplayName(request.nombre().trim()); user.setCargo(request.rol().name().equals("MUNICIPIO") ? "Gestión municipal" : request.rol().name().equals("ADMIN_SISTEMA") ? "Administración del sistema" : "Fiscalizador Técnico de Campo"); user.setPasswordHash(encoder.encode(request.password())); user.setRole(request.rol()); user.getRoles().add(role); user.setActivo(true); user.setCreatedAt(LocalDateTime.now(ZoneId.of("America/Lima")));
        return toResponse(users.save(user));
    }
    @Transactional(readOnly = true) public java.util.List<FiscalizadorDisponibleResponse> fiscalizadoresDisponibles() {
        return users.findAllByOrderByUsernameAsc().stream()
                .filter(u -> u.isActivo() && u.getRoles().stream().anyMatch(r -> r.getName().equals("FISCALIZADOR")) && u.getFiscalizador() != null && u.getFiscalizador().isActivo())
                .map(u -> { var f=u.getFiscalizador(); return new FiscalizadorDisponibleResponse(f.getId(), f.getNombre(), f.getZona(), f.isActivo()); })
                .toList();
    }
    @Transactional public UsuarioResponse cambiarEstado(String username, boolean activo) { var u=users.findByUsernameIgnoreCase(username).orElseThrow(); u.setActivo(activo); return toResponse(u); }
    @Transactional public UsuarioResponse asignarRoles(String username, java.util.List<String> roleNames) {
        if (!canManageRoles() && roleNames.stream().anyMatch(r -> r.equals("ADMIN_SISTEMA"))) throw new org.springframework.security.access.AccessDeniedException("No puedes asignar ADMIN_SISTEMA.");
        var u=users.findByUsernameIgnoreCase(username).orElseThrow();
        var found=roles.findAll().stream().filter(r -> roleNames.contains(r.getName())).toList();
        if (found.size()!=new java.util.HashSet<>(roleNames).size() || found.isEmpty()) throw new IllegalArgumentException("Selecciona al menos un rol válido.");
        u.getRoles().clear(); u.getRoles().addAll(found); u.setRole(pe.gob.munisanmiguel.entity.Rol.valueOf(found.get(0).getName())); return toResponse(u);
    }
    private boolean canManageRoles() { return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLES_GESTIONAR")); }
    private UsuarioResponse toResponse(AppUser user) { return new UsuarioResponse(user.getUsername(), user.getDisplayName(), user.getRoles().stream().map(AppRole::getName).sorted().findFirst().orElse(""), user.isActivo(), user.getCreatedAt() == null ? LocalDate.now().toString() : user.getCreatedAt().toLocalDate().toString(), user.getRoles().stream().map(AppRole::getName).sorted().toList()); }
}

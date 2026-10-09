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
import pe.gob.munisanmiguel.repository.FiscalizadorRepository;
import pe.gob.munisanmiguel.entity.AppRole;
import pe.gob.munisanmiguel.entity.Fiscalizador;
import pe.gob.munisanmiguel.entity.Rol;
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
    private final FiscalizadorRepository fiscalizadores;
    private final AdminSettingsService settings;
    public UsuarioService(AppUserRepository users, PasswordEncoder encoder, AppRoleRepository roles,FiscalizadorRepository fiscalizadores,AdminSettingsService settings) { this.users = users; this.encoder = encoder; this.roles = roles; this.fiscalizadores=fiscalizadores;this.settings=settings; }
    @Transactional(readOnly = true) public java.util.List<UsuarioResponse> listar() { return users.findAllByOrderByUsernameAsc().stream().map(this::toResponse).toList(); }
    @Transactional public UsuarioResponse crear(UsuarioRequest request) {
        if (request.rol() == Rol.ADMIN_SISTEMA) throw new org.springframework.security.access.AccessDeniedException("No se pueden crear administradores desde este formulario.");
        if(request.password()==null||request.password().length()<8)throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
        if (request.confirmarPassword()!=null && !request.confirmarPassword().isBlank() && !request.password().equals(request.confirmarPassword())) throw new IllegalArgumentException("Las contraseñas no coinciden.");
        if (users.findByUsernameIgnoreCase(request.username().trim()).isPresent()) throw new ConflictException("El nombre de usuario ya existe.");
        var role = roles.findByName(request.rol().name()).orElseThrow();
        var user = new AppUser(); user.setUsername(request.username().trim()); user.setDisplayName(request.nombre().trim()); user.setCargo(request.rol().name().equals("MUNICIPIO") ? "Gestión municipal" : request.rol().name().equals("ADMIN_SISTEMA") ? "Administración del sistema" : "Fiscalizador Técnico de Campo"); user.setPasswordHash(encoder.encode(request.password())); user.setRole(request.rol()); user.getRoles().add(role); user.setActivo(true); user.setCreatedAt(LocalDateTime.now(ZoneId.of("America/Lima")));
        users.saveAndFlush(user);
        if(request.rol()==Rol.FISCALIZADOR)vincularFiscalizador(user,request.fiscalizadorId());
        else if(request.fiscalizadorId()!=null&&!request.fiscalizadorId().isBlank())throw new ConflictException("Solo una cuenta fiscalizadora puede vincularse al directorio operativo.");
        settings.createDefaults(user);
        return toResponse(user);
    }
    @Transactional(readOnly=true) public java.util.List<FiscalizadorDisponibleResponse> fiscalizadoresSinCuenta(){
        return fiscalizadores.findAll().stream().filter(f->f.isActivo()&&!users.existsByFiscalizadorId(f.getId()))
                .sorted(java.util.Comparator.comparing(Fiscalizador::getNombre))
                .map(f->new FiscalizadorDisponibleResponse(f.getId(),f.getNombre(),f.getZona(),true)).toList();
    }
    @Transactional(readOnly = true) public java.util.List<FiscalizadorDisponibleResponse> fiscalizadoresDisponibles() {
        return users.findAllByOrderByUsernameAsc().stream()
                .filter(u -> u.isActivo() && u.getRoles().stream().anyMatch(r -> r.getName().equals("FISCALIZADOR")) && u.getFiscalizador() != null && u.getFiscalizador().isActivo())
                .map(u -> { var f=u.getFiscalizador(); return new FiscalizadorDisponibleResponse(f.getId(), f.getNombre(), f.getZona(), f.isActivo()); })
                .toList();
    }
    @Transactional public UsuarioResponse cambiarEstado(String username, boolean activo) { var u=users.findByUsernameIgnoreCase(username).orElseThrow();
        if(!activo && u.isActivo() && u.getRoles().stream().anyMatch(r->"ADMIN_SISTEMA".equals(r.getName())) &&
                users.activeAdminsForUpdate().stream().noneMatch(other->!other.getId().equals(u.getId())))
            throw new ConflictException("No se puede desactivar el último administrador activo.");
        u.setActivo(activo); return toResponse(u); }
    @Transactional public UsuarioResponse asignarRoles(String username, java.util.List<String> roleNames,String fiscalizadorId) {
        if (!canManageRoles() && roleNames.stream().anyMatch(r -> r.equals("ADMIN_SISTEMA"))) throw new org.springframework.security.access.AccessDeniedException("No puedes asignar ADMIN_SISTEMA.");
        var u=users.findByUsernameIgnoreCase(username).orElseThrow();
        if(u.isActivo()&&u.getRoles().stream().anyMatch(r->"ADMIN_SISTEMA".equals(r.getName()))&&!roleNames.contains("ADMIN_SISTEMA")&&
                users.activeAdminsForUpdate().stream().noneMatch(other->!other.getId().equals(u.getId())))
            throw new ConflictException("No se puede retirar el rol del último administrador activo.");
        var found=roles.findAll().stream().filter(r -> roleNames.contains(r.getName())).toList();
        if (found.size()!=new java.util.HashSet<>(roleNames).size() || found.isEmpty()) throw new IllegalArgumentException("Selecciona al menos un rol válido.");
        u.getRoles().clear(); u.getRoles().addAll(found); u.setRole(pe.gob.munisanmiguel.entity.Rol.valueOf(found.get(0).getName()));
        if(found.stream().anyMatch(r->"FISCALIZADOR".equals(r.getName())))vincularFiscalizador(u,fiscalizadorId);
        else if(fiscalizadorId!=null&&!fiscalizadorId.isBlank())throw new ConflictException("El usuario necesita el rol FISCALIZADOR para vincularse.");
        return toResponse(u);
    }
    private void vincularFiscalizador(AppUser user,String requestedId){
        if(user.getFiscalizador()!=null){
            if(requestedId!=null&&!requestedId.isBlank()&&!user.getFiscalizador().getId().equals(requestedId))
                throw new ConflictException("El usuario ya está vinculado a otro fiscalizador.");
            return;
        }
        if(user.getId()==null)users.saveAndFlush(user);
        if(requestedId!=null&&!requestedId.isBlank()){
            var selected=fiscalizadores.findById(requestedId).orElseThrow(()->new ConflictException("Fiscalizador operativo no encontrado."));
            if(!selected.isActivo()||users.existsByFiscalizadorId(selected.getId()))
                throw new ConflictException("El fiscalizador operativo está inactivo o ya tiene una cuenta.");
            user.setFiscalizador(selected);return;
        }
        if(fiscalizadores.findAll().stream().anyMatch(f->f.getNombre().trim().equalsIgnoreCase(user.getDisplayName().trim())))
            throw new ConflictException("Ya existe un fiscalizador con ese nombre; selecciónalo al crear o asignar el rol.");
        String id="F-U-"+Long.toString(user.getId(),36).toUpperCase(java.util.Locale.ROOT);
        if(fiscalizadores.existsById(id))throw new ConflictException("El identificador operativo ya existe; solicita vinculación explícita.");
        var nuevo=new Fiscalizador();nuevo.setId(id);nuevo.setCodigo(id);
        nuevo.setNombre(user.getDisplayName());nuevo.setZona("");nuevo.setTelefono("");nuevo.setActivo(true);
        user.setFiscalizador(fiscalizadores.save(nuevo));
    }
    private boolean canManageRoles() { var auth=SecurityContextHolder.getContext().getAuthentication();return auth!=null&&auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLES_GESTIONAR")); }
    private UsuarioResponse toResponse(AppUser user) { return new UsuarioResponse(user.getUsername(), user.getDisplayName(), user.getRoles().stream().map(AppRole::getName).sorted().findFirst().orElse(""), user.isActivo(), user.getCreatedAt() == null ? LocalDate.now().toString() : user.getCreatedAt().toLocalDate().toString(), user.getRoles().stream().map(AppRole::getName).sorted().toList(),user.getFiscalizador()==null?null:user.getFiscalizador().getId(),user.getFiscalizador()==null?null:user.getFiscalizador().getNombre()); }
}

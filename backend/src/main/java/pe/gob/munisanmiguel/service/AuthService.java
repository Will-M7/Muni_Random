package pe.gob.munisanmiguel.service;

import pe.gob.munisanmiguel.dto.ApiDtos.*; import pe.gob.munisanmiguel.exception.InvalidCredentialsException; import pe.gob.munisanmiguel.repository.AppUserRepository; import pe.gob.munisanmiguel.security.JwtService; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.stereotype.Service;

@Service public class AuthService {
    private final AppUserRepository users; private final PasswordEncoder encoder; private final JwtService jwt;
    public AuthService(AppUserRepository users, PasswordEncoder encoder, JwtService jwt) { this.users=users; this.encoder=encoder; this.jwt=jwt; }
    public LoginResponse login(LoginRequest request) {
        var identifier = request.identifier();
        if (identifier == null || identifier.isBlank()) throw new InvalidCredentialsException();
        var user = users.findByUsernameIgnoreCaseAndActivoTrue(identifier.trim()).orElseThrow(InvalidCredentialsException::new);
        if (!encoder.matches(request.password(), user.getPasswordHash())) throw new InvalidCredentialsException();
        var roles = user.getRoles().stream().map(r -> r.getName()).sorted().toList();
        if (request.rol()!=null && !request.rol().isBlank() && !roles.stream().anyMatch(r -> r.equalsIgnoreCase(request.rol()))) throw new InvalidCredentialsException();
        String fid = user.getFiscalizador()==null ? "" : user.getFiscalizador().getId();
        String primaryRole = request.rol()!=null && roles.stream().anyMatch(r -> r.equalsIgnoreCase(request.rol())) ? request.rol().toUpperCase() : roles.stream().findFirst().orElse("");
        var capabilities = user.getRoles().stream().flatMap(r -> r.getCapabilities().stream()).map(c -> c.getCode()).distinct().sorted().toList();
        return new LoginResponse(jwt.create(user.getUsername(), fid), user.getDisplayName(), user.getCargo(), user.getUsername(), primaryRole, fid, roles, capabilities);
    }
}

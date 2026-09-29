package pe.gob.munisanmiguel.controller;
import pe.gob.munisanmiguel.dto.ApiDtos.*; import pe.gob.munisanmiguel.service.AuthService; import jakarta.validation.Valid; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth") public class AuthController { private final AuthService service; public AuthController(AuthService s){service=s;} @PostMapping("/login") public LoginResponse login(@Valid @RequestBody LoginRequest request){return service.login(request);} }

package pe.gob.munisanmiguel.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.gob.munisanmiguel.service.IdentityIntegrationService;
import pe.gob.munisanmiguel.service.IdentityIntegrationService.*;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;

@RestController @RequestMapping("/api/identity-integration")
public class IdentityIntegrationController {
    public record TestDniRequest(@NotBlank @Pattern(regexp="\\d{8}",message="El DNI debe tener exactamente 8 dígitos.") String dni) {}
    private final IdentityIntegrationService service;
    public IdentityIntegrationController(IdentityIntegrationService service){this.service=service;}
    @GetMapping("/availability") public Map<String,Boolean> availability(){return Map.of("enabled",service.available());}
    @GetMapping("/quota") public IdentityIntegrationService.DniQuotaStatus quota(){return service.quotaStatus();}
    @GetMapping public Configuration get(Authentication auth){return service.configuration(auth.getName());}
    @PutMapping public Configuration update(@RequestBody Update request,Authentication auth){return service.update(request,auth.getName());}
    @PostMapping("/test") public Map<String,String> test(Authentication auth){return Map.of("message",service.testConnection(auth.getName()));}
    @PostMapping("/test-dni") public DniTestResult testDni(@Valid @RequestBody TestDniRequest request,Authentication auth){return service.testDni(request.dni(),auth.getName());}
}

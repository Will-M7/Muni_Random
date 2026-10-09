package pe.gob.munisanmiguel.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.gob.munisanmiguel.service.IdentityIntegrationService;
import pe.gob.munisanmiguel.service.IdentityIntegrationService.*;
import java.util.Map;

@RestController @RequestMapping("/api/identity-integration")
public class IdentityIntegrationController {
    private final IdentityIntegrationService service;
    public IdentityIntegrationController(IdentityIntegrationService service){this.service=service;}
    @GetMapping("/availability") public Map<String,Boolean> availability(){return Map.of("enabled",service.available());}
    @GetMapping public Configuration get(Authentication auth){return service.configuration(auth.getName());}
    @PutMapping public Configuration update(@RequestBody Update request,Authentication auth){return service.update(request,auth.getName());}
    @PostMapping("/test") public Map<String,String> test(Authentication auth){return Map.of("message",service.testConnection(auth.getName()));}
}

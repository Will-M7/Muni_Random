package pe.gob.munisanmiguel.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.gob.munisanmiguel.dto.ApiDtos.*;
import pe.gob.munisanmiguel.service.RoleCapabilityService;

@RestController @RequestMapping("/api/admin") @PreAuthorize("hasAuthority('ROLES_VER')")
public class AdminController {
    private final RoleCapabilityService service;
    public AdminController(RoleCapabilityService service) { this.service=service; }
    @GetMapping("/roles") public java.util.List<RoleResponse> roles() { return service.roles(); }
    @GetMapping("/capacidades") @PreAuthorize("hasAuthority('CAPACIDADES_GESTIONAR')") public java.util.List<CapabilityResponse> capabilities() { return service.capabilities(); }
    @PutMapping("/roles/{nombre}/capacidades") @PreAuthorize("hasAuthority('ROLES_GESTIONAR')") public RoleResponse setCapabilities(@PathVariable String nombre, @Valid @RequestBody RoleCapabilitiesRequest body) { return service.setCapabilities(nombre,body); }
}

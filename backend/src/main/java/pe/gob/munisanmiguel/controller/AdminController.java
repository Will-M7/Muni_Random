package pe.gob.munisanmiguel.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.gob.munisanmiguel.dto.ApiDtos.*;
import pe.gob.munisanmiguel.service.RoleCapabilityService;

@RestController @RequestMapping("/api/admin") @PreAuthorize("hasAuthority('ROLES_VER')")
public class AdminController {
    private final RoleCapabilityService service;
    private final pe.gob.munisanmiguel.service.AdminSettingsService settings;
    public AdminController(RoleCapabilityService service,pe.gob.munisanmiguel.service.AdminSettingsService settings) { this.service=service;this.settings=settings; }
    @GetMapping("/roles") public java.util.List<RoleResponse> roles(org.springframework.security.core.Authentication auth) { settings.requireAdmin(auth.getName());return service.roles(); }
    @GetMapping("/capacidades") @PreAuthorize("hasAuthority('CAPACIDADES_GESTIONAR')") public java.util.List<CapabilityResponse> capabilities(org.springframework.security.core.Authentication auth) { settings.requireAdmin(auth.getName());return service.capabilities(); }
    @PutMapping("/roles/{nombre}/capacidades") @PreAuthorize("hasAuthority('ROLES_GESTIONAR')") public RoleResponse setCapabilities(@PathVariable String nombre, @Valid @RequestBody RoleCapabilitiesRequest body,org.springframework.security.core.Authentication auth) { settings.requireAdmin(auth.getName());return service.setCapabilities(nombre,body); }
}

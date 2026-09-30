package pe.gob.munisanmiguel.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.gob.munisanmiguel.dto.ApiDtos.*;
import pe.gob.munisanmiguel.repository.AppRoleRepository;
import pe.gob.munisanmiguel.repository.CapabilityRepository;

@Service
public class RoleCapabilityService {
    private final AppRoleRepository roles;
    private final CapabilityRepository capabilities;
    public RoleCapabilityService(AppRoleRepository roles, CapabilityRepository capabilities) { this.roles=roles; this.capabilities=capabilities; }
    @Transactional(readOnly=true) public java.util.List<RoleResponse> roles() { return roles.findAll().stream().sorted(java.util.Comparator.comparing(r->r.getName())).map(r->new RoleResponse(r.getName(),r.getDescription(),r.isConfigurable(),r.getCapabilities().stream().map(c->c.getCode()).sorted().toList())).toList(); }
    @Transactional(readOnly=true) public java.util.List<CapabilityResponse> capabilities() { return capabilities.findAll().stream().sorted(java.util.Comparator.comparing(c->c.getCategory()+c.getCode())).map(c->new CapabilityResponse(c.getCode(),c.getDescription(),c.getCategory())).toList(); }
    @Transactional public RoleResponse setCapabilities(String roleName, RoleCapabilitiesRequest request) {
        var role=roles.findByName(roleName).orElseThrow();
        if (!role.isConfigurable()) throw new IllegalArgumentException("Este rol no es configurable.");
        var found=capabilities.findAll().stream().filter(c->request.capacidades().contains(c.getCode())).toList();
        if (found.size()!=new java.util.HashSet<>(request.capacidades()).size()) throw new IllegalArgumentException("La lista contiene capacidades desconocidas.");
        role.getCapabilities().clear(); role.getCapabilities().addAll(found);
        return new RoleResponse(role.getName(),role.getDescription(),role.isConfigurable(),role.getCapabilities().stream().map(c->c.getCode()).sorted().toList());
    }
}

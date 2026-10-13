package pe.gob.munisanmiguel.service;

import org.junit.jupiter.api.Test;
import pe.gob.munisanmiguel.entity.AppRole;
import pe.gob.munisanmiguel.entity.Capability;
import pe.gob.munisanmiguel.repository.AppRoleRepository;
import pe.gob.munisanmiguel.repository.CapabilityRepository;
import pe.gob.munisanmiguel.dto.ApiDtos.RoleCapabilitiesRequest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoleCapabilityServiceTest {
    private final List<String> management=List.of("ROLES_VER","ROLES_GESTIONAR","CAPACIDADES_GESTIONAR");
    private record Fixture(RoleCapabilityService service,AppRole role){}
    private Fixture fixture(String name){
        var roles=mock(AppRoleRepository.class);var capabilities=mock(CapabilityRepository.class);
        var role=new AppRole();role.setName(name);role.setConfigurable(true);
        var catalog=new ArrayList<Capability>();for(String code:List.of("ROLES_VER","ROLES_GESTIONAR","CAPACIDADES_GESTIONAR","EXPEDIENTE_VER")){
            var cap=new Capability();cap.setCode(code);catalog.add(cap);
            if(management.contains(code))role.getCapabilities().add(cap);
        }
        when(roles.findByName(name)).thenReturn(Optional.of(role));when(roles.findByName(name.toLowerCase(Locale.ROOT))).thenReturn(Optional.of(role));when(capabilities.findAll()).thenReturn(catalog);
        return new Fixture(new RoleCapabilityService(roles,capabilities),role);
    }
    @Test void preservesAdministrativeRecoveryWhenAnyManagementCapabilityIsRemoved(){
        var f=fixture("ADMIN_SISTEMA");for(String code:management){var requested=new ArrayList<>(management);requested.remove(code);
            assertThrows(IllegalArgumentException.class,()->f.service.setCapabilities("ADMIN_SISTEMA",new RoleCapabilitiesRequest(requested)));
            assertThrows(IllegalArgumentException.class,()->f.service.setCapabilities("admin_sistema",new RoleCapabilitiesRequest(requested)));
            assertEquals(3,f.role.getCapabilities().size());
        }
    }
    @Test void doesNotAutomaticallyGrantOperationalCapabilities(){
        var f=fixture("ADMIN_SISTEMA");var response=f.service.setCapabilities("ADMIN_SISTEMA",new RoleCapabilitiesRequest(management));
        assertEquals(new HashSet<>(management),new HashSet<>(response.capacidades()));assertFalse(response.capacidades().contains("EXPEDIENTE_VER"));
    }
    @Test void keepsOtherRolesConfigurable(){
        var f=fixture("MUNICIPIO");assertEquals(List.of("EXPEDIENTE_VER"),f.service.setCapabilities("MUNICIPIO",new RoleCapabilitiesRequest(List.of("EXPEDIENTE_VER"))).capacidades());
    }
}

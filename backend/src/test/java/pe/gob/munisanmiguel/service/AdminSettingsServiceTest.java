package pe.gob.munisanmiguel.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.repository.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminSettingsServiceTest {
    private final AppUserRepository users=mock(AppUserRepository.class);
    private final UserPreferenceRepository preferences=mock(UserPreferenceRepository.class);
    private final GlobalParameterRepository parameters=mock(GlobalParameterRepository.class);
    private final AdminSettingsService service=new AdminSettingsService(users,preferences,parameters);
    private AppUser user(String name,Rol role){var u=new AppUser();u.setId(1L);u.setUsername(name);u.setRole(role);u.setActivo(true);var r=new AppRole();r.setName(role.name());u.getRoles().add(r);return u;}
    @Test void ajustesMunicipalesRequierenUnMetodoYQuedanPersistidos(){
        when(users.findByUsernameIgnoreCaseAndActivoTrue("admin")).thenReturn(Optional.of(user("admin",Rol.ADMIN_SISTEMA)));
        when(users.findByUsernameIgnoreCase("muni")).thenReturn(Optional.of(user("muni",Rol.MUNICIPIO)));
        when(preferences.save(any())).thenAnswer(i->i.getArgument(0));
        assertThrows(IllegalArgumentException.class,()->service.update("muni",new AdminSettingsService.Preferences(false,false,false,true),"admin"));
        var result=service.update("muni",new AdminSettingsService.Preferences(true,false,false,true),"admin");
        assertTrue(result.mapaInterno());assertFalse(result.googleExterno());
        verify(preferences).save(argThat(p->p.getUserId()==1L&&"admin".equals(p.getActualizadoPor())&&p.getActualizadoEn()!=null));
    }
    @Test void soloAdministradorModificaYLimiteEsOrientativoPositivo(){
        when(users.findByUsernameIgnoreCaseAndActivoTrue("muni")).thenReturn(Optional.of(user("muni",Rol.MUNICIPIO)));
        assertThrows(AccessDeniedException.class,()->service.updateLimit(8,"muni"));
        when(users.findByUsernameIgnoreCaseAndActivoTrue("admin")).thenReturn(Optional.of(user("admin",Rol.ADMIN_SISTEMA)));
        assertThrows(IllegalArgumentException.class,()->service.updateLimit(0,"admin"));
        when(parameters.save(any())).thenAnswer(i->i.getArgument(0));
        assertEquals(8,service.updateLimit(8,"admin").limite());
    }
}

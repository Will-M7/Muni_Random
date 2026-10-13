package pe.gob.munisanmiguel.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.gob.munisanmiguel.dto.ApiDtos.UsuarioEdicionRequest;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.exception.ConflictException;
import pe.gob.munisanmiguel.repository.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UsuarioGestionTest {
    private UsuarioService service(AppUserRepository users, FiscalizadorRepository directory, UserPreferenceRepository preferences, UserHistoryService history, AppRoleRepository roles, PasswordEncoder encoder) {
        return new UsuarioService(users, encoder, roles, directory, mock(AdminSettingsService.class), preferences, history);
    }

    @Test void editaNombreUsuarioYRestableceContrasenaSinCambiarIdentidad() {
        var users=mock(AppUserRepository.class);var user=new AppUser();user.setId(7L);user.setUsername("antes");user.setDisplayName("Nombre anterior");user.setRole(Rol.MUNICIPIO);
        var muni=new AppRole();muni.setName("MUNICIPIO");user.getRoles().add(muni);
        var roles=mock(AppRoleRepository.class);when(roles.findByName("MUNICIPIO")).thenReturn(Optional.of(muni));
        when(users.findByUsernameIgnoreCase("antes")).thenReturn(Optional.of(user));when(users.findByUsernameIgnoreCase("despues")).thenReturn(Optional.empty());
        var encoder=mock(PasswordEncoder.class);when(encoder.encode("ClaveNueva8")).thenReturn("hash-nuevo");
        service(users,mock(FiscalizadorRepository.class),mock(UserPreferenceRepository.class),mock(UserHistoryService.class),roles,encoder)
                .editar("antes",new UsuarioEdicionRequest("despues","Nombre actualizado","ClaveNueva8",Rol.MUNICIPIO));
        assertEquals(7L,user.getId());assertEquals("despues",user.getUsername());assertEquals("Nombre actualizado",user.getDisplayName());assertEquals("hash-nuevo",user.getPasswordHash());
    }

    @Test void soloEliminaFisicamenteCuandoNoHayReferencias() {
        var users=mock(AppUserRepository.class);var user=new AppUser();user.setId(12L);user.setUsername("temporal");user.setActivo(true);
        when(users.findByUsernameIgnoreCase("temporal")).thenReturn(Optional.of(user));
        var preferences=mock(UserPreferenceRepository.class);var history=mock(UserHistoryService.class);when(history.referencesToUser(user)).thenReturn(List.of());
        var result=service(users,mock(FiscalizadorRepository.class),preferences,history,mock(AppRoleRepository.class),mock(PasswordEncoder.class)).eliminarOArchivar("temporal","admin");
        assertEquals("ELIMINADA",result.accion());verify(preferences).deleteById(12L);verify(users).delete(user);
    }

    @Test void archivaYConservaUnaCuentaConHistorial() {
        var users=mock(AppUserRepository.class);var user=new AppUser();user.setId(19L);user.setUsername("con-historial");user.setActivo(true);
        when(users.findByUsernameIgnoreCase("con-historial")).thenReturn(Optional.of(user));
        var history=mock(UserHistoryService.class);when(history.referencesToUser(user)).thenReturn(List.of("expediente_fiscalizacion.creado_por_id (2)"));
        var result=service(users,mock(FiscalizadorRepository.class),mock(UserPreferenceRepository.class),history,mock(AppRoleRepository.class),mock(PasswordEncoder.class)).eliminarOArchivar("con-historial","admin");
        assertEquals("ARCHIVADA",result.accion());assertFalse(user.isActivo());assertNotNull(user.getArchivedAt());verify(users,never()).delete(any());
    }

    @Test void impideEliminarLaPropiaCuentaAdministrativa() {
        var users=mock(AppUserRepository.class);var user=new AppUser();user.setId(1L);user.setUsername("admin");user.setActivo(true);
        when(users.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));
        assertThrows(ConflictException.class,()->service(users,mock(FiscalizadorRepository.class),mock(UserPreferenceRepository.class),mock(UserHistoryService.class),mock(AppRoleRepository.class),mock(PasswordEncoder.class)).eliminarOArchivar("admin","admin"));
        verify(users,never()).delete(any());
    }

    @Test void impideArchivarElUltimoAdministradorActivo() {
        var users=mock(AppUserRepository.class);var user=new AppUser();user.setId(2L);user.setUsername("admin-secundario");user.setActivo(true);
        var role=new AppRole();role.setName("ADMIN_SISTEMA");user.getRoles().add(role);
        when(users.findByUsernameIgnoreCase("admin-secundario")).thenReturn(Optional.of(user));when(users.activeAdminsForUpdate()).thenReturn(List.of(user));
        var service=service(users,mock(FiscalizadorRepository.class),mock(UserPreferenceRepository.class),mock(UserHistoryService.class),mock(AppRoleRepository.class),mock(PasswordEncoder.class));
        assertThrows(ConflictException.class,()->service.eliminarOArchivar("admin-secundario","admin-principal"));
        verify(users,never()).delete(any());assertNull(user.getArchivedAt());
    }
    @Test void noPermiteRetirarElRolDelUltimoAdministradorActivo(){
        var users=mock(AppUserRepository.class);var user=new AppUser();user.setId(3L);user.setUsername("admin-solo");user.setActivo(true);
        var adminRole=new AppRole();adminRole.setName("ADMIN_SISTEMA");user.getRoles().add(adminRole);
        when(users.findByUsernameIgnoreCase("admin-solo")).thenReturn(Optional.of(user));when(users.activeAdminsForUpdate()).thenReturn(List.of(user));
        var service=service(users,mock(FiscalizadorRepository.class),mock(UserPreferenceRepository.class),mock(UserHistoryService.class),mock(AppRoleRepository.class),mock(PasswordEncoder.class));
        assertThrows(ConflictException.class,()->service.asignarRoles("admin-solo",List.of("MUNICIPIO"),null));
        assertTrue(user.getRoles().contains(adminRole));
    }

    @Test void responsableMunicipalDebeReasignarFiscalizadoresAntesDeRetirarRol(){
        var users=mock(AppUserRepository.class);var u=new AppUser();u.setId(40L);u.setUsername("responsable");u.setDisplayName("Responsable");u.setRole(Rol.MUNICIPIO);
        var role=new AppRole();role.setName("MUNICIPIO");u.getRoles().add(role);when(users.findByUsernameIgnoreCase("responsable")).thenReturn(Optional.of(u));
        var directory=mock(FiscalizadorRepository.class);when(directory.existsByMunicipioResponsableId(40L)).thenReturn(true);
        var svc=service(users,directory,mock(UserPreferenceRepository.class),mock(UserHistoryService.class),mock(AppRoleRepository.class),mock(PasswordEncoder.class));
        assertThrows(ConflictException.class,()->svc.editar("responsable",new UsuarioEdicionRequest("responsable","Responsable",null,Rol.FISCALIZADOR)));
        assertThrows(ConflictException.class,()->svc.asignarRoles("responsable",List.of("FISCALIZADOR"),null));assertEquals(Rol.MUNICIPIO,u.getRole());assertNull(u.getFiscalizador());
    }

}

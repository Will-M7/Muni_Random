package pe.gob.munisanmiguel.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.gob.munisanmiguel.dto.ApiDtos.UsuarioRequest;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.repository.*;
import pe.gob.munisanmiguel.exception.ConflictException;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UsuarioFiscalizadorAltaTest {
    @Test void crearCuentaFiscalizadorVinculaIdentidadOperativa(){
        var users=mock(AppUserRepository.class);var roles=mock(AppRoleRepository.class);
        var fiscalizadores=mock(FiscalizadorRepository.class);var encoder=mock(PasswordEncoder.class);
        var role=new AppRole();role.setName("FISCALIZADOR");
        when(roles.findByName("FISCALIZADOR")).thenReturn(Optional.of(role));
        when(users.findByUsernameIgnoreCase("inspector")).thenReturn(Optional.empty());
        when(users.saveAndFlush(any())).thenAnswer(i->{AppUser u=i.getArgument(0);u.setId(42L);return u;});
        when(fiscalizadores.save(any())).thenAnswer(i->i.getArgument(0));
        var service=new UsuarioService(users,encoder,roles,fiscalizadores);
        service.crear(new UsuarioRequest("inspector","Inspector de prueba","12345678","12345678",Rol.FISCALIZADOR,null));
        verify(fiscalizadores).save(argThat(f->"F-U-16".equals(f.getId())&&f.isActivo()));
        verify(users).saveAndFlush(argThat(u->u.getFiscalizador()!=null||u.getId()==42L));
    }

    @Test void asignarRolFiscalizadorAUsuarioExistenteNoDuplicaVinculo(){
        var users=mock(AppUserRepository.class);var roles=mock(AppRoleRepository.class);
        var fiscalizadores=mock(FiscalizadorRepository.class);
        var user=new AppUser();user.setId(5L);user.setUsername("existente");
        var fiscalizador=new Fiscalizador();fiscalizador.setId("F-U-5");user.setFiscalizador(fiscalizador);
        var role=new AppRole();role.setName("FISCALIZADOR");
        when(users.findByUsernameIgnoreCase("existente")).thenReturn(Optional.of(user));
        when(roles.findAll()).thenReturn(List.of(role));
        new UsuarioService(users,mock(PasswordEncoder.class),roles,fiscalizadores).asignarRoles("existente",List.of("FISCALIZADOR"),null);
        assertSame(fiscalizador,user.getFiscalizador());verifyNoInteractions(fiscalizadores);
    }

    @Test void vinculaRegistroExistenteSinCrearOtro(){
        var users=mock(AppUserRepository.class);var roles=mock(AppRoleRepository.class);
        var fiscalizadores=mock(FiscalizadorRepository.class);var encoder=mock(PasswordEncoder.class);
        var role=new AppRole();role.setName("FISCALIZADOR");
        var existing=new Fiscalizador();existing.setId("FIS-001");existing.setActivo(true);
        when(roles.findByName("FISCALIZADOR")).thenReturn(Optional.of(role));
        when(users.saveAndFlush(any())).thenAnswer(i->{AppUser u=i.getArgument(0);u.setId(43L);return u;});
        when(fiscalizadores.findById("FIS-001")).thenReturn(Optional.of(existing));
        new UsuarioService(users,encoder,roles,fiscalizadores).crear(
                new UsuarioRequest("inspector2","Inspector dos","12345678","12345678",Rol.FISCALIZADOR,"FIS-001"));
        verify(fiscalizadores,never()).save(any());
        verify(users).saveAndFlush(argThat(u->u.getFiscalizador()==existing));
    }

    @Test void nombreOperativoExistenteExigeVinculacionExplicita(){
        var users=mock(AppUserRepository.class);var roles=mock(AppRoleRepository.class);
        var fiscalizadores=mock(FiscalizadorRepository.class);
        var role=new AppRole();role.setName("FISCALIZADOR");
        var existing=new Fiscalizador();existing.setId("FIS-001");existing.setNombre("Inspector de prueba");
        when(roles.findByName("FISCALIZADOR")).thenReturn(Optional.of(role));
        when(users.saveAndFlush(any())).thenAnswer(i->{AppUser u=i.getArgument(0);u.setId(44L);return u;});
        when(fiscalizadores.findAll()).thenReturn(List.of(existing));
        var service=new UsuarioService(users,mock(PasswordEncoder.class),roles,fiscalizadores);
        assertThrows(ConflictException.class,()->service.crear(
                new UsuarioRequest("inspector3","Inspector de prueba","12345678","12345678",Rol.FISCALIZADOR,null)));
        verify(fiscalizadores,never()).save(any());
    }
}

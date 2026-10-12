package pe.gob.munisanmiguel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pe.gob.munisanmiguel.entity.IdentityIntegration;
import pe.gob.munisanmiguel.repository.IdentityIntegrationRepository;
import java.util.Base64;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IdentityIntegrationServiceTest {
    private IdentityIntegrationService service(IdentityIntegrationRepository repo){
        var admin=mock(AdminSettingsService.class);
        when(repo.save(any())).thenAnswer(i->i.getArgument(0));
        return new IdentityIntegrationService(repo,admin,new ObjectMapper(),mock(DniDailyQuotaService.class),Base64.getEncoder().encodeToString(new byte[32]),"api.entidad.gob.pe",org.springframework.web.client.RestClient.builder().build());
    }
    @Test void cifraClaveYNoLaDevuelve(){
        var repo=mock(IdentityIntegrationRepository.class);var service=service(repo);
        var response=service.update(new IdentityIntegrationService.Update("PERUAPI_COM",null,"clave-privada-ficticia-solo-pruebas-000",null,null),"admin");
        assertTrue(response.credentialConfigured());assertNull(response.endpoint());
        verify(repo).save(argThat(c->c.getEncryptedPeruapiKey()!=null&&!c.getEncryptedPeruapiKey().contains("clave-privada-ficticia-solo-pruebas-000")));
    }
    @Test void informaFaltaDeClaveMaestraSinConvertirlaEnFalloTecnico(){
        var repo=mock(IdentityIntegrationRepository.class);when(repo.save(any())).thenAnswer(i->i.getArgument(0));
        var admin=mock(AdminSettingsService.class);var service=new IdentityIntegrationService(repo,admin,new ObjectMapper(),mock(DniDailyQuotaService.class),"","api.entidad.gob.pe",org.springframework.web.client.RestClient.builder().build());
        var error=assertThrows(pe.gob.munisanmiguel.exception.ConflictException.class,()->service.update(new IdentityIntegrationService.Update("PERUAPI_COM",null,"credencial-ficticia-para-pruebas-sin-valor-real",null,null),"admin"));
        assertTrue(error.getMessage().contains("Falta IDENTITY_MASTER_KEY"));verify(repo,never()).save(any());
    }
    @Test void guardaCredencialFicticiaSinConsultarProveedorExterno(){
        var repo=mock(IdentityIntegrationRepository.class);when(repo.save(any())).thenAnswer(i->i.getArgument(0));var service=service(repo);
        var response=service.update(new IdentityIntegrationService.Update("PERUAPI_COM",null,"fake-test-credential-never-valid-000000",null,null),"admin");
        assertEquals("PERUAPI_COM",response.provider());assertTrue(response.credentialConfigured());
        verify(repo).save(argThat(c->c.getEncryptedPeruapiKey()!=null&&!c.getEncryptedPeruapiKey().contains("fake-test-credential-never-valid-000000")));
    }
    @Test void normalizaRespuestaDePeruApiCom() throws Exception {
        var service=service(mock(IdentityIntegrationRepository.class));
        var value=service.mapResponse(new ObjectMapper().readTree("{\"dni\":\"60012345\",\"nombres\":\"JULIO FERNANDO\",\"apellido_paterno\":\"QUISPE\",\"apellido_materno\":\"PEREZ\"}"),"60012345");
        assertEquals("JULIO FERNANDO",value.nombres());assertEquals("QUISPE",value.apellidoPaterno());assertEquals("PEREZ",value.apellidoMaterno());
    }
    @Test void rechazaEndpointReniecNoAutorizado(){
        var repo=mock(IdentityIntegrationRepository.class);var service=service(repo);
        assertThrows(IllegalArgumentException.class,()->service.update(new IdentityIntegrationService.Update("RENIEC","https://127.0.0.1/lookup","clave",null,null),"admin"));
        verify(repo,never()).save(any());
    }
    @Test void desactivarConservaCredencialAnterior(){
        var repo=mock(IdentityIntegrationRepository.class);var existing=new IdentityIntegration();existing.setId((byte)1);existing.setProvider("PERUAPI_COM");existing.setEncryptedPeruapiKey("secret-cifrado");
        when(repo.findById((byte)1)).thenReturn(Optional.of(existing));var service=service(repo);
        var response=service.update(new IdentityIntegrationService.Update("DISABLED",null,null,null,null),"admin");
        assertFalse(response.credentialConfigured());assertEquals("secret-cifrado",existing.getEncryptedPeruapiKey());
    }
}

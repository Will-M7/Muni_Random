package pe.gob.munisanmiguel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pe.gob.munisanmiguel.entity.IdentityIntegration;
import pe.gob.munisanmiguel.exception.ConflictException;
import pe.gob.munisanmiguel.exception.NotFoundException;
import pe.gob.munisanmiguel.exception.ReniecNotConfiguredException;
import pe.gob.munisanmiguel.exception.ReniecUnavailableException;
import pe.gob.munisanmiguel.exception.TooManyRequestsException;
import pe.gob.munisanmiguel.repository.IdentityIntegrationRepository;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class IdentityIntegrationLiveTest {
    private record Fixture(IdentityIntegrationService service,MockRestServiceServer server,IdentityIntegrationRepository repository,DniDailyQuotaService quota) {}
    private Fixture fixture(String masterKey){
        return fixture(masterKey,"PERUAPI_COM");
    }
    private Fixture fixture(String masterKey,String provider){
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        var repo=mock(IdentityIntegrationRepository.class);var state=new AtomicReference<IdentityIntegration>();
        when(repo.findById((byte)1)).thenAnswer(i->Optional.ofNullable(state.get()));
        when(repo.save(any())).thenAnswer(i->{IdentityIntegration saved=i.getArgument(0);state.set(saved);return saved;});
        var admin=mock(AdminSettingsService.class);doAnswer(i->{if(!"admin".equals(i.getArgument(0)))throw new AccessDeniedException("admin only");return null;}).when(admin).requireAdmin(anyString());
        var quota=mock(DniDailyQuotaService.class);when(quota.reserve(any())).thenReturn(true);
        var service=new IdentityIntegrationService(repo,admin,new ObjectMapper(),quota,masterKey,"api.entidad.gob.pe",builder.build());
        service.update(new IdentityIntegrationService.Update(provider,provider.equals("RENIEC")?"https://api.entidad.gob.pe/api/dni/{dni}":null,"fake-api-key-for-local-test-only-credential",null,null),"admin");
        return new Fixture(service,server,repo,quota);
    }
    private static final String OK="{\"dni\":\"00000000\",\"nombres\":\"PRUEBA\",\"apellido_paterno\":\"FICTICIO\",\"apellido_materno\":\"SIMULADO\",\"code\":\"200\"}";
    private static final String FACTILIZA_OK="{\"status\":200,\"success\":true,\"data\":{\"numero\":\"00000000\",\"nombres\":\"NOMBRE\",\"apellido_paterno\":\"PATERNO\",\"apellido_materno\":\"MATERNO\",\"direccion\":\"DIRECCION PRIVADA\"}}";
    @Test void factilizaUsaBearerYMapeaSoloNombresSinCuotaLocal(){
        var f=fixture(Base64.getEncoder().encodeToString(new byte[32]),"FACTILIZA");
        f.server().expect(requestTo("https://api.factiliza.com/v1/dni/info/00000000"))
            .andExpect(header("Authorization","Bearer fake-api-key-for-local-test-only-credential"))
            .andRespond(withSuccess(FACTILIZA_OK,MediaType.APPLICATION_JSON));
        var result=f.service().testDni("00000000","admin");
        assertEquals("FACTILIZA",result.provider());assertEquals("NOMBRE",result.nombres());assertEquals("PATERNO",result.apellidoPaterno());assertEquals("MATERNO",result.apellidoMaterno());
        assertFalse(f.service().quotaStatus().applies());verify(f.quota(),never()).reserve(any());verify(f.quota(),never()).used(any());f.server().verify();
    }
    @Test void factilizaDistingueCredencialDniCuotaCaidaYDatosIncompletos(){
        var key=Base64.getEncoder().encodeToString(new byte[32]);
        var invalid=fixture(key,"FACTILIZA");invalid.server().expect(requestTo("https://api.factiliza.com/v1/dni/info/00000000")).andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED));assertThrows(ConflictException.class,()->invalid.service().findByDni("00000000"));
        var missing=fixture(key,"FACTILIZA");missing.server().expect(requestTo("https://api.factiliza.com/v1/dni/info/00000000")).andRespond(withSuccess("{\"status\":404,\"success\":false,\"message\":\"DNI no encontrado\"}",MediaType.APPLICATION_JSON));assertThrows(NotFoundException.class,()->missing.service().findByDni("00000000"));
        var quota=fixture(key,"FACTILIZA");quota.server().expect(requestTo("https://api.factiliza.com/v1/dni/info/00000000")).andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));assertThrows(TooManyRequestsException.class,()->quota.service().findByDni("00000000"));
        var plan=fixture(key,"FACTILIZA");plan.server().expect(requestTo("https://api.factiliza.com/v1/dni/info/00000000")).andRespond(withSuccess("{\"status\":400,\"success\":false,\"message\":\"Cuota excedida\"}",MediaType.APPLICATION_JSON));assertThrows(TooManyRequestsException.class,()->plan.service().findByDni("00000000"));
        var down=fixture(key,"FACTILIZA");down.server().expect(requestTo("https://api.factiliza.com/v1/dni/info/00000000")).andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));assertThrows(ReniecUnavailableException.class,()->down.service().findByDni("00000000"));
        var incomplete=fixture(key,"FACTILIZA");incomplete.server().expect(requestTo("https://api.factiliza.com/v1/dni/info/00000000")).andRespond(withSuccess("{\"status\":200,\"success\":true,\"data\":{\"numero\":\"00000000\",\"nombres\":\"NOMBRE\"}}",MediaType.APPLICATION_JSON));assertThrows(ReniecUnavailableException.class,()->incomplete.service().findByDni("00000000"));
        for(var f:new Fixture[]{invalid,missing,quota,plan,down,incomplete}){verify(f.quota(),never()).reserve(any());f.server().verify();}
    }
    @Test void credencialesCifradasPersistenEntreProveedoresYReinicioDelServicio(){
        var key=Base64.getEncoder().encodeToString(new byte[32]);var f=fixture(key,"PERUAPI_COM");
        f.service().update(new IdentityIntegrationService.Update("FACTILIZA",null,"token-ficticio-para-prueba",null,null),"admin");
        var config=f.repository().findById((byte)1).orElseThrow();
        assertNotNull(config.getEncryptedPeruapiKey());assertNotNull(config.getEncryptedFactilizaToken());
        assertFalse(config.getEncryptedFactilizaToken().contains("token-ficticio"));
        String firstToken=config.getEncryptedFactilizaToken();
        f.service().update(new IdentityIntegrationService.Update("FACTILIZA",null,"token-ficticio-reemplazo",null,null),"admin");
        assertNotEquals(firstToken,config.getEncryptedFactilizaToken());
        f.service().update(new IdentityIntegrationService.Update("RENIEC","https://api.entidad.gob.pe/api/dni/{dni}",null,null,null),"admin");
        f.service().update(new IdentityIntegrationService.Update("DISABLED",null,null,null,null),"admin");
        f.service().update(new IdentityIntegrationService.Update("FACTILIZA",null,null,null,null),"admin");
        var restarted=new IdentityIntegrationService(f.repository(),mock(AdminSettingsService.class),new ObjectMapper(),f.quota(),key,"api.entidad.gob.pe",RestClient.builder().build());
        assertTrue(restarted.available());assertTrue(restarted.configuration("admin").credentialConfigured());
        f.service().update(new IdentityIntegrationService.Update("PERUAPI_COM",null,null,null,null),"admin");
        assertTrue(restarted.available());
    }
    @Test void testDniUsesConfiguredPeruApiHeaderAndReturnsUnifiedData(){
        var f=fixture(Base64.getEncoder().encodeToString(new byte[32]));f.server().expect(requestTo("https://peruapi.com/api/dni/00000000")).andExpect(header("X-API-KEY","fake-api-key-for-local-test-only-credential")).andRespond(withSuccess(OK,MediaType.APPLICATION_JSON));
        var result=f.service().testDni("00000000","admin");
        assertEquals("PERUAPI_COM",result.provider());assertEquals("PRUEBA",result.nombres());assertEquals("SIMULADO",result.apellidoMaterno());assertEquals("Consulta realizada correctamente.",result.message());verify(f.repository(),times(1)).save(any());verify(f.quota()).reserve(any());f.server().verify();
    }
    @Test void distingueDniNoEncontrado(){var f=fixture(Base64.getEncoder().encodeToString(new byte[32]));f.server().expect(requestTo("https://peruapi.com/api/dni/00000000")).andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));assertThrows(NotFoundException.class,()->f.service().testDni("00000000","admin"));}
    @Test void distingueCredencialRechazadaIpYCuota(){
        var key=Base64.getEncoder().encodeToString(new byte[32]);var credential=fixture(key);credential.server().expect(requestTo("https://peruapi.com/api/dni/00000000")).andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED).body("{\"mensaje\":\"Token inválido\"}"));var rejected=assertThrows(ConflictException.class,()->credential.service().testDni("00000000","admin"));assertTrue(rejected.getMessage().contains("clave de PerúAPI"));
        var ip=fixture(key);ip.server().expect(requestTo("https://peruapi.com/api/dni/00000000")).andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED).body("{\"mensaje\":\"IP 192.0.2.1 no autorizada\"}"));assertTrue(assertThrows(ConflictException.class,()->ip.service().testDni("00000000","admin")).getMessage().contains("IP pública de salida"));verify(ip.repository(),times(1)).save(any());
        var quota=fixture(key);quota.server().expect(requestTo("https://peruapi.com/api/dni/00000000")).andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).body("{\"mensaje\":\"Cuota excedida (daily/monthly)\"}"));assertTrue(assertThrows(TooManyRequestsException.class,()->quota.service().testDni("00000000","admin")).getMessage().contains("cuota"));
    }
    @Test void proveedorDesactivadoClaveIncorrectaYAccesoNoAdmin(){
        var key=Base64.getEncoder().encodeToString(new byte[32]);var f=fixture(key);
        assertThrows(AccessDeniedException.class,()->f.service().testDni("00000000","municipio"));assertThrows(AccessDeniedException.class,()->f.service().testDni("00000000","fiscalizador"));
        var wrong=new IdentityIntegrationService(f.repository(),mock(AdminSettingsService.class),new ObjectMapper(),f.quota(),Base64.getEncoder().encodeToString(new byte[32]).replace("A","B"),"",RestClient.builder().build());
        assertFalse(wrong.available());assertThrows(ConflictException.class,()->wrong.testDni("00000000","admin"));
        f.service().update(new IdentityIntegrationService.Update("DISABLED",null,null,null,null),"admin");assertThrows(ReniecNotConfiguredException.class,()->f.service().testDni("00000000","admin"));
    }
    @Test void distingueProveedorTemporalmenteNoDisponibleYErrorDeConexion(){
        var key=Base64.getEncoder().encodeToString(new byte[32]);var service=fixture(key);service.server().expect(requestTo("https://peruapi.com/api/dni/00000000")).andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));assertThrows(ReniecUnavailableException.class,()->service.service().testDni("00000000","admin"));
        var down=fixture(key);down.server().expect(requestTo("https://peruapi.com/api/dni/00000000")).andRespond(withException(new IOException("private transport detail")));var message=assertThrows(ReniecUnavailableException.class,()->down.service().testDni("00000000","admin")).getMessage();assertEquals("No se pudo conectar temporalmente con el proveedor DNI.",message);assertFalse(message.contains("private"));
    }
    @Test void aplicaLimiteDeFrecuenciaYValidaFormatoAntesDeLaSolicitud(){var f=fixture(Base64.getEncoder().encodeToString(new byte[32]));assertThrows(IllegalArgumentException.class,()->f.service().testDni("123","admin"));f.server().expect(requestTo("https://peruapi.com/api/dni/00000000")).andRespond(withSuccess(OK,MediaType.APPLICATION_JSON));f.service().testDni("00000000","admin");assertThrows(TooManyRequestsException.class,()->f.service().testDni("00000000","admin"));}
    @Test void cuotaAgotadaImpideEnviarYValidacionesLocalesNoConsumen(){var f=fixture(Base64.getEncoder().encodeToString(new byte[32]));assertThrows(IllegalArgumentException.class,()->f.service().findByDni("123"));verify(f.quota(),never()).reserve(any());when(f.quota().reserve(any())).thenReturn(false);var error=assertThrows(TooManyRequestsException.class,()->f.service().findByDni("00000000"));assertTrue(error.getMessage().contains("45 consultas DNI"));f.server().verify();}
    @Test void falloRemotoConsumeUnaReserva(){var f=fixture(Base64.getEncoder().encodeToString(new byte[32]));f.server().expect(requestTo("https://peruapi.com/api/dni/00000000")).andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));assertThrows(ReniecUnavailableException.class,()->f.service().findByDni("00000000"));verify(f.quota()).reserve(any());}
    @Test void cuotaSeAplicaSoloAPeruApiYElConsumoSobreviveAlCambioDeProveedor(){
        var key=Base64.getEncoder().encodeToString(new byte[32]);var f=fixture(key,"RENIEC");var count=new java.util.concurrent.atomic.AtomicInteger(44);
        when(f.quota().used(any())).thenAnswer(i->count.get());when(f.quota().reserve(any())).thenAnswer(i->{while(true){int current=count.get();if(current>=45)return false;if(count.compareAndSet(current,current+1))return true;}});
        var reniecStatus=f.service().quotaStatus();assertFalse(reniecStatus.applies());assertEquals("RENIEC",reniecStatus.provider());verify(f.quota(),never()).used(any());verify(f.quota(),never()).reserve(any());
        f.service().update(new IdentityIntegrationService.Update("PERUAPI_COM",null,"fake-api-key-for-local-test-only-credential-2",null,null),"admin");
        var resumed=f.service().quotaStatus();assertTrue(resumed.applies());assertEquals(44,resumed.used());assertEquals(1,resumed.remaining());
        f.server().expect(requestTo("https://peruapi.com/api/dni/00000000")).andRespond(withSuccess(OK,MediaType.APPLICATION_JSON));
        f.service().findByDni("00000000");assertEquals(45,f.service().quotaStatus().used());
        assertThrows(TooManyRequestsException.class,()->f.service().findByDni("00000000"));f.server().verify();verify(f.quota(),times(2)).reserve(any());
    }
    @Test void reniecRespetaLimiteRemotoSinUsarCuotaLocal(){var f=fixture(Base64.getEncoder().encodeToString(new byte[32]),"RENIEC");f.server().expect(requestTo("https://api.entidad.gob.pe/api/dni/00000000")).andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).body("provider limit"));assertTrue(assertThrows(TooManyRequestsException.class,()->f.service().findByDni("00000000")).getMessage().contains("RENIEC indicó"));verify(f.quota(),never()).reserve(any());verify(f.quota(),never()).used(any());f.server().verify();}
    @Test void identificaVariantesDelErrorDeIpSinConfundirClaveRechazada(){
        assertTrue(IdentityIntegrationService.isPeruApiIpNotAuthorized("IP address is not authorized"));
        assertTrue(IdentityIntegrationService.isPeruApiIpNotAuthorized("La dirección IP no está permitida"));
        assertFalse(IdentityIntegrationService.isPeruApiIpNotAuthorized("API key invalid"));
        assertFalse(IdentityIntegrationService.isPeruApiIpNotAuthorized("IP field is required"));
    }
}

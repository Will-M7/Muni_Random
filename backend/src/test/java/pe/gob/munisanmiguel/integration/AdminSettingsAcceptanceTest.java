package pe.gob.munisanmiguel.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.repository.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="municipal.integration", matches="true")
@org.springframework.test.context.ContextConfiguration(initializers=IsolatedDatabaseInitializer.class)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties={"spring.datasource.url=${municipal.test.jdbc-url}","app.storage.path=target/admin-acceptance/solicitudes"})
class AdminSettingsAcceptanceTest {
    @Autowired TestRestTemplate http;
    @Autowired AppUserRepository users;
    @Autowired AppRoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired UserPreferenceRepository preferences;
    @Autowired FiscalizadorRepository fiscalizadores;
    @Autowired JdbcTemplate jdbc;
    private HttpEntity<?> auth(String token,Object body){var headers=new HttpHeaders();headers.setBearerAuth(token);headers.setContentType(MediaType.APPLICATION_JSON);return new HttpEntity<>(body,headers);}
    private JsonNode json(HttpMethod method,String path,String token,Object body,HttpStatus expected){var result=http.exchange(path,method,auth(token,body),JsonNode.class);assertEquals(expected,result.getStatusCode(),path+": "+result.getBody());return result.getBody();}
    @Test void adminCreaCuentasYAplicaAjustesSinPerderHistorial(){
        String suffix=UUID.randomUUID().toString().substring(0,8);
        String adminName="acc_admin_"+suffix,muniName="acc_muni_"+suffix,fiscalName="acc_fiscal_"+suffix,deleteName="acc_delete_"+suffix,archiveName="acc_archive_"+suffix;
        var admin=new AppUser();admin.setUsername(adminName);admin.setDisplayName("Admin fixture");admin.setCargo("Prueba");admin.setRole(Rol.ADMIN_SISTEMA);admin.getRoles().add(roles.findByName("ADMIN_SISTEMA").orElseThrow());admin.setPasswordHash(encoder.encode("Test123456"));admin.setActivo(true);admin.setCreatedAt(LocalDateTime.now());users.saveAndFlush(admin);
        try{
            var login=http.postForEntity("/api/auth/login",Map.of("username",adminName,"password","Test123456"),JsonNode.class);
            assertEquals(HttpStatus.OK,login.getStatusCode());String token=login.getBody().path("token").asText();assertFalse(token.isBlank());
            var muni=json(HttpMethod.POST,"/api/usuarios",token,Map.of("username",muniName,"nombre","Municipio fixture","password","12345678","rol","MUNICIPIO"),HttpStatus.CREATED);
            assertEquals("MUNICIPIO",muni.path("rol").asText());
            String muniToken=http.postForEntity("/api/auth/login",Map.of("username",muniName,"password","12345678"),JsonNode.class).getBody().path("token").asText();
            var fiscal=json(HttpMethod.POST,"/api/usuarios",token,Map.of("username",fiscalName,"nombre","Fiscalizador fixture "+suffix,"password","12345678","rol","FISCALIZADOR"),HttpStatus.CREATED);
            assertFalse(fiscal.path("fiscalizadorId").asText().isBlank());
            json(HttpMethod.POST,"/api/usuarios",token,Map.of("username",muniName,"nombre","Duplicado","password","12345678","rol","MUNICIPIO"),HttpStatus.CONFLICT);
            json(HttpMethod.PUT,"/api/settings/users/"+muniName,token,Map.of("mapaInterno",false,"googleExterno",true,"googleUrl",false,"descargarJornadaHtml",true),HttpStatus.OK);
            assertFalse(json(HttpMethod.GET,"/api/settings/users/"+muniName,token,null,HttpStatus.OK).path("mapaInterno").asBoolean());
            var profile=json(HttpMethod.PUT,"/api/usuarios/"+muniName+"/perfil",token,Map.of("username",muniName+"_edit","nombre","Municipio editado","password","ClaveNueva8","rol","MUNICIPIO"),HttpStatus.OK);
            muniName=profile.path("username").asText();assertEquals("Municipio editado",profile.path("nombre").asText());
            assertFalse(json(HttpMethod.GET,"/api/settings/users/"+muniName,token,null,HttpStatus.OK).path("mapaInterno").asBoolean());
            json(HttpMethod.PUT,"/api/settings/users/"+fiscalName,token,Map.of("mapaInterno",true,"googleExterno",true,"googleUrl",true,"descargarJornadaHtml",false),HttpStatus.OK);
            var fiscalLogin=http.postForEntity("/api/auth/login",Map.of("username",fiscalName,"password","12345678"),JsonNode.class);
            String fiscalToken=fiscalLogin.getBody().path("token").asText();
            var htmlHeaders=new HttpHeaders();htmlHeaders.setBearerAuth(fiscalToken);htmlHeaders.setAccept(java.util.List.of(MediaType.TEXT_HTML));
            assertEquals(HttpStatus.FORBIDDEN,http.exchange("/api/fiscalizador/mis-visitas.html",HttpMethod.GET,new HttpEntity<>(htmlHeaders),String.class).getStatusCode());
            String opId=fiscal.path("fiscalizadorId").asText();
            assertEquals("ELIMINADA",json(HttpMethod.DELETE,"/api/usuarios/cuentas/"+fiscalName,token,null,HttpStatus.OK).path("accion").asText());
            assertFalse(fiscalizadores.existsById(opId));
            json(HttpMethod.PATCH,"/api/usuarios/"+muniName+"/estado",token,Map.of("activo",false),HttpStatus.OK);
            assertEquals(HttpStatus.UNAUTHORIZED,http.postForEntity("/api/auth/login",Map.of("username",muniName,"password","12345678"),JsonNode.class).getStatusCode());
            assertEquals(HttpStatus.UNAUTHORIZED,http.exchange("/api/settings/me",HttpMethod.GET,auth(muniToken,null),JsonNode.class).getStatusCode());
            json(HttpMethod.PATCH,"/api/usuarios/"+muniName+"/estado",token,Map.of("activo",true),HttpStatus.OK);
            assertEquals(HttpStatus.OK,http.postForEntity("/api/auth/login",Map.of("username",muniName,"password","ClaveNueva8"),JsonNode.class).getStatusCode());
            json(HttpMethod.POST,"/api/usuarios",token,Map.of("username",deleteName,"nombre","Cuenta desechable","password","12345678","rol","MUNICIPIO"),HttpStatus.CREATED);
            var deleted=json(HttpMethod.DELETE,"/api/usuarios/cuentas/"+deleteName,token,null,HttpStatus.OK);assertEquals("ELIMINADA",deleted.path("accion").asText());
            assertFalse(users.findByUsernameIgnoreCase(deleteName).isPresent());
            json(HttpMethod.POST,"/api/usuarios",token,Map.of("username",archiveName,"nombre","Cuenta con referencia","password","12345678","rol","MUNICIPIO"),HttpStatus.CREATED);
            Long archiveId=users.findByUsernameIgnoreCase(archiveName).orElseThrow().getId();String expediente="ACC-"+suffix;
            jdbc.update("INSERT INTO expediente_fiscalizacion(codigo,fecha_creacion,creado_por_id,origen,estado,objeto_fiscalizacion) VALUES (?,NOW(),?,'DENUNCIA','REGISTRADO','Fixture de aceptación')",expediente,archiveId);
            var archived=json(HttpMethod.DELETE,"/api/usuarios/cuentas/"+archiveName,token,null,HttpStatus.OK);assertEquals("ARCHIVADA",archived.path("accion").asText());
            assertEquals(HttpStatus.UNAUTHORIZED,http.postForEntity("/api/auth/login",Map.of("username",archiveName,"password","12345678"),JsonNode.class).getStatusCode());
            json(HttpMethod.PATCH,"/api/usuarios/"+archiveName+"/archivo",token,Map.of("archivado",false),HttpStatus.OK);
            assertEquals(HttpStatus.OK,http.postForEntity("/api/auth/login",Map.of("username",archiveName,"password","12345678"),JsonNode.class).getStatusCode());
            jdbc.update("DELETE FROM expediente_fiscalizacion WHERE codigo=?",expediente);
        }finally{
            for(String username:new String[]{muniName,fiscalName,deleteName,archiveName,adminName})users.findByUsernameIgnoreCase(username).ifPresent(u->{
                jdbc.update("DELETE FROM expediente_fiscalizacion WHERE creado_por_id=?",u.getId());preferences.deleteById(u.getId());var f=u.getFiscalizador();users.delete(u);users.flush();if(f!=null&&!jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM programacion_fiscalizacion WHERE fiscalizador_id=?) OR EXISTS(SELECT 1 FROM solicitud WHERE fiscalizador_id=?)",Boolean.class,f.getId(),f.getId()))fiscalizadores.delete(f);
            });
        }
    }
}

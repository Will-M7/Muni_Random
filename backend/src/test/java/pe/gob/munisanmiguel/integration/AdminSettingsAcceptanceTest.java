package pe.gob.munisanmiguel.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.repository.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties={"spring.datasource.url=jdbc:mysql://localhost:3307/san_miguel_pruebas_v20?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Lima&characterEncoding=utf8","app.storage.path=target/admin-acceptance/solicitudes"})
class AdminSettingsAcceptanceTest {
    @Autowired TestRestTemplate http;
    @Autowired AppUserRepository users;
    @Autowired AppRoleRepository roles;
    @Autowired PasswordEncoder encoder;
    private HttpEntity<?> auth(String token,Object body){var headers=new HttpHeaders();headers.setBearerAuth(token);headers.setContentType(MediaType.APPLICATION_JSON);return new HttpEntity<>(body,headers);}
    private JsonNode json(HttpMethod method,String path,String token,Object body,HttpStatus expected){var result=http.exchange(path,method,auth(token,body),JsonNode.class);assertEquals(expected,result.getStatusCode(),path+": "+result.getBody());return result.getBody();}
    @Test void adminCreaCuentasYAplicaAjustesSinPerderHistorial(){
        String suffix=UUID.randomUUID().toString().substring(0,8);
        String adminName="acc_admin_"+suffix,muniName="acc_muni_"+suffix,fiscalName="acc_fiscal_"+suffix;
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
            json(HttpMethod.PUT,"/api/settings/users/"+fiscalName,token,Map.of("mapaInterno",true,"googleExterno",true,"googleUrl",true,"descargarJornadaHtml",false),HttpStatus.OK);
            var fiscalLogin=http.postForEntity("/api/auth/login",Map.of("username",fiscalName,"password","12345678"),JsonNode.class);
            String fiscalToken=fiscalLogin.getBody().path("token").asText();
            var htmlHeaders=new HttpHeaders();htmlHeaders.setBearerAuth(fiscalToken);htmlHeaders.setAccept(java.util.List.of(MediaType.TEXT_HTML));
            assertEquals(HttpStatus.FORBIDDEN,http.exchange("/api/fiscalizador/mis-visitas.html",HttpMethod.GET,new HttpEntity<>(htmlHeaders),String.class).getStatusCode());
            json(HttpMethod.PATCH,"/api/usuarios/"+muniName+"/estado",token,Map.of("activo",false),HttpStatus.OK);
            assertEquals(HttpStatus.UNAUTHORIZED,http.postForEntity("/api/auth/login",Map.of("username",muniName,"password","12345678"),JsonNode.class).getStatusCode());
            assertEquals(HttpStatus.UNAUTHORIZED,http.exchange("/api/settings/me",HttpMethod.GET,auth(muniToken,null),JsonNode.class).getStatusCode());
            json(HttpMethod.PATCH,"/api/usuarios/"+muniName+"/estado",token,Map.of("activo",true),HttpStatus.OK);
            assertEquals(HttpStatus.OK,http.postForEntity("/api/auth/login",Map.of("username",muniName,"password","12345678"),JsonNode.class).getStatusCode());
        }finally{
            for(String username:new String[]{muniName,fiscalName,adminName})users.findByUsernameIgnoreCase(username).ifPresent(u->{u.setActivo(false);users.save(u);});
        }
    }
}

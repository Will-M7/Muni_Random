package pe.gob.munisanmiguel.integration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.repository.*;
import pe.gob.munisanmiguel.service.UsuarioService;
import pe.gob.munisanmiguel.dto.ApiDtos.UsuarioRequest;
import org.springframework.util.LinkedMultiValueMap;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="municipal.integration",matches="true")
@ContextConfiguration(initializers=IsolatedDatabaseInitializer.class)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties={"spring.datasource.url=${municipal.test.jdbc-url}","app.storage.path=target/municipal-agenda/solicitudes","app.storage.diligencias-path=target/municipal-agenda/diligencias"})
class MunicipalAgendaIntegrationTest {
    @Autowired TestRestTemplate http;@Autowired UsuarioService accounts;@Autowired AppUserRepository users;@Autowired AppRoleRepository roles;@Autowired PasswordEncoder encoder;@Autowired JdbcTemplate jdbc;
    private final String suffix=UUID.randomUUID().toString().substring(0,8);private final ZoneId lima=ZoneId.of("America/Lima");
    private AppUser account(String label,Rol role){String name=label+suffix;if(role!=Rol.ADMIN_SISTEMA)accounts.crear(new UsuarioRequest(name,name,"Isolated123!",null,role,null));else{var a=new AppUser();a.setUsername(name);a.setDisplayName(name);a.setCargo("TEST");a.setRole(role);a.getRoles().add(roles.findByName(role.name()).orElseThrow());a.setPasswordHash(encoder.encode("Isolated123!"));a.setActivo(true);a.setCreatedAt(LocalDateTime.now(lima));users.saveAndFlush(a);}return users.findByUsernameIgnoreCaseAndActivoTrue(name).orElseThrow();}
    private String login(AppUser user){return http.postForEntity("/api/auth/login",Map.of("username",user.getUsername(),"password","Isolated123!"),JsonNode.class).getBody().path("token").asText();}
    private ResponseEntity<JsonNode> call(HttpMethod method,String path,String token,Object body){var headers=new HttpHeaders();headers.setBearerAuth(token);headers.setContentType(MediaType.APPLICATION_JSON);return http.exchange(path,method,new HttpEntity<>(body,headers),JsonNode.class);}
    private JsonNode ok(HttpMethod method,String path,String token,Object body,HttpStatus expected){var result=call(method,path,token,body);assertEquals(expected,result.getStatusCode(),path+" "+result.getBody());return result.getBody();}
    private Map<String,Object> data(){return Map.of("origen","INICIATIVA_MUNICIPAL","objetoFiscalizacion","Prueba aislada "+suffix,"direccionFiscalizada","Dirección sintética de integración");}
    @Test void asignacionExplicitaCalendarioMensualSeguridadYActualizacionReal(){
        var admin=account("mg-admin-",Rol.ADMIN_SISTEMA);var a=account("mg-a-",Rol.MUNICIPIO);var b=account("mg-b-",Rol.MUNICIPIO);var inspector=account("mg-field-",Rol.FISCALIZADOR);
        String adminToken=login(admin),ta=login(a),tb=login(b),tf=login(inspector),id=inspector.getFiscalizador().getId();
        assertEquals(HttpStatus.FORBIDDEN,call(HttpMethod.PUT,"/api/admin/fiscalizadores-gestion/"+id,ta,Map.of("municipioUsername",a.getUsername())).getStatusCode());
        ok(HttpMethod.PUT,"/api/admin/fiscalizadores-gestion/"+id,adminToken,Map.of("municipioUsername",a.getUsername()),HttpStatus.OK);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM fiscalizador_gestion_historial WHERE fiscalizador_id=? AND actor_id=? AND municipio_nuevo_id=?",Integer.class,id,admin.getId(),a.getId()));
        assertTrue(ok(HttpMethod.GET,"/api/catalogos/fiscalizadores",tb,null,HttpStatus.OK).isEmpty());
        var e=ok(HttpMethod.POST,"/api/expedientes",ta,data(),HttpStatus.CREATED);String code=e.path("codigo").asText();
        assertEquals(HttpStatus.FORBIDDEN,call(HttpMethod.GET,"/api/expedientes/"+code,tb,null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,call(HttpMethod.GET,"/api/expedientes/"+code+"/documentos",tb,null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,call(HttpMethod.GET,"/api/programaciones/agenda/mensual?mes=2026-10",tf,null).getStatusCode());
        LocalDate date=LocalDate.now(lima).plusDays(2);String month=YearMonth.from(date).toString();int limit=ok(HttpMethod.GET,"/api/settings/daily-limit",ta,null,HttpStatus.OK).path("limite").asInt();
        List<Long> ids=new ArrayList<>();for(int i=0;i<=limit;i++)ids.add(ok(HttpMethod.POST,"/api/expedientes/"+code+"/programaciones",ta,Map.of("fiscalizadorId",id,"fecha",date.toString(),"hora",LocalTime.of(8,0).plusMinutes(5L*i).toString(),"tipoVisita","PROGRAMADA"),HttpStatus.CREATED).path("id").asLong());
        var summary=ok(HttpMethod.GET,"/api/programaciones/agenda/mensual?mes="+month,ta,null,HttpStatus.OK);assertEquals("MUNICIPAL",summary.path("alcance").asText());assertEquals(YearMonth.from(date).lengthOfMonth(),summary.path("dias").size());assertEquals(limit+1,summary.path("visitas").size());
        JsonNode day=null;for(var d:summary.path("dias"))if(d.path("fecha").asText().equals(date.toString()))day=d;assertNotNull(day);assertEquals("rojo",day.path("color").asText());
        assertTrue(ok(HttpMethod.GET,"/api/programaciones/agenda/mensual?mes="+month,tb,null,HttpStatus.OK).path("visitas").isEmpty());
        assertEquals(HttpStatus.CONFLICT,call(HttpMethod.POST,"/api/expedientes/"+code+"/programaciones",ta,Map.of("fiscalizadorId",id,"fecha",date.toString(),"hora","08:00","tipoVisita","PROGRAMADA")).getStatusCode());
        ok(HttpMethod.POST,"/api/programaciones/"+ids.getLast()+"/cancelar",ta,Map.of("motivo","Cancelación aislada"),HttpStatus.OK);
        var refreshed=ok(HttpMethod.GET,"/api/programaciones/agenda/mensual?mes="+month,ta,null,HttpStatus.OK);for(var d:refreshed.path("dias"))if(d.path("fecha").asText().equals(date.toString())){assertEquals(limit,d.path("carga").asInt());assertEquals("amarillo",d.path("color").asText());}
        ok(HttpMethod.PUT,"/api/admin/fiscalizadores-gestion/"+id,adminToken,Map.of("municipioUsername",b.getUsername()),HttpStatus.OK);
        assertEquals(HttpStatus.FORBIDDEN,call(HttpMethod.POST,"/api/expedientes/"+code+"/programaciones",ta,Map.of("fiscalizadorId",id,"fecha",date.plusDays(1).toString(),"hora","09:00","tipoVisita","PROGRAMADA")).getStatusCode());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM fiscalizador_gestion_historial WHERE fiscalizador_id=? AND actor_id=?",Integer.class,id,admin.getId()));
        assertEquals(inspector.getId(),users.findByFiscalizadorId(id).orElseThrow().getId());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='27'",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='29' AND success=1",Integer.class));
    }
    @Test void altaMultipartSinDependenciaConReferenciaYPreferenciasDisponibles(){
        var a=account("no-dep-",Rol.MUNICIPIO);String token=login(a);assertTrue(ok(HttpMethod.GET,"/api/settings/me",token,null,HttpStatus.OK).path("mapaInterno").asBoolean());
        var data=new HashMap<String,Object>(data());data.put("origen","SOLICITUD");data.put("latitud",-15.4);data.put("longitud",-70.1);
        var partHeaders=new HttpHeaders();partHeaders.setContentType(MediaType.APPLICATION_JSON);var form=new LinkedMultiValueMap<String,Object>();form.add("fiscalizacion",new HttpEntity<>(Map.of("expediente",data,"referenciaSolicitud","REF-"+suffix,"metodoUbicacion","MAPA_INTERNO"),partHeaders));
        var headers=new HttpHeaders();headers.setBearerAuth(token);headers.setContentType(MediaType.MULTIPART_FORM_DATA);var result=http.exchange("/api/expedientes/fiscalizaciones",HttpMethod.POST,new HttpEntity<>(form,headers),JsonNode.class);
        assertEquals(HttpStatus.CREATED,result.getStatusCode(),Objects.toString(result.getBody()));assertTrue(result.getBody().path("dependenciaProcedencia").isNull());assertEquals("REF-"+suffix,result.getBody().path("referenciaSolicitud").asText());
    }
}

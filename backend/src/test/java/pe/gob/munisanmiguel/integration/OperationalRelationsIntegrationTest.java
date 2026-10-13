package pe.gob.munisanmiguel.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.dto.ApiDtos.*;
import pe.gob.munisanmiguel.repository.*;
import pe.gob.munisanmiguel.service.*;
import pe.gob.munisanmiguel.exception.ConflictException;
import java.time.*;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="municipal.integration",matches="true")
@ContextConfiguration(initializers=IsolatedDatabaseInitializer.class)
@SpringBootTest
@TestPropertySource(properties={"spring.datasource.url=${municipal.test.jdbc-url}","app.storage.path=target/identity-relations/solicitudes","app.storage.diligencias-path=target/identity-relations/diligencias"})
@Transactional
class OperationalRelationsIntegrationTest {
    @Autowired UsuarioService accounts;
    @Autowired AppUserRepository users;
    @Autowired ExpedienteFiscalizacionService cases;
    @Autowired DiligenciaFiscalizacionService inspections;
    @Autowired ExpedienteHistorialRepository history;
    @Autowired ProgramacionFiscalizacionRepository visits;
    @Autowired FiscalizadorRepository directory;
    private final String suffix=UUID.randomUUID().toString().substring(0,8);
    private AppUser create(String label,Rol role) {
        String name=label+suffix;accounts.crear(new UsuarioRequest(name,name,"Test123456",null,role,null));
        return users.findByUsernameIgnoreCase(name).orElseThrow();
    }
    private String expediente(AppUser actor,String label) {
        return cases.crear(new ExpedienteRequest(OrigenFiscalizacion.INICIATIVA_MUNICIPAL,null,label,null,"PERSONA_NATURAL","00000000","Persona sintética","900000000","Dirección de prueba",null,"Prueba",null,null,null),actor.getUsername()).codigo();
    }
    @Test void unMunicipioGestionaDosFiscalizadoresYMultiplesExpedientesVisitasYDiligencias() {
        var muni=create("muni-",Rol.MUNICIPIO);var owner=create("owner-",Rol.FISCALIZADOR);var other=create("other-",Rol.FISCALIZADOR);
        assertNull(muni.getFiscalizador());assertNotEquals(owner.getFiscalizador().getId(),other.getFiscalizador().getId());
        owner.getFiscalizador().setMunicipioResponsable(muni);other.getFiscalizador().setMunicipioResponsable(muni);directory.flush();
        String a=expediente(muni,"Expediente A"),b=expediente(muni,"Expediente B");
        var today=LocalDate.now(ZoneId.of("America/Lima"));String fid=owner.getFiscalizador().getId();
        var first=cases.programar(a,new ProgramacionRequest(fid,today,LocalTime.of(9,0),TipoVisita.INOPINADA),muni.getUsername());
        var second=cases.programar(b,new ProgramacionRequest(fid,today,LocalTime.of(11,0),TipoVisita.INOPINADA),muni.getUsername());
        var extra=cases.programar(a,new ProgramacionRequest(other.getFiscalizador().getId(),today.plusDays(1),LocalTime.of(9,0),TipoVisita.PROGRAMADA),muni.getUsername());
        assertNotEquals(first.id(),second.id());assertNotEquals(first.id(),extra.id());
        assertEquals(2,cases.misProgramaciones(owner.getUsername()).size());assertEquals(1,cases.misProgramaciones(other.getUsername()).size());
        inspections.iniciar(first.id(),new InicioDiligenciaRequest(EstadoGps.NO_AUTORIZADO,null,null),owner.getUsername());
        inspections.iniciar(second.id(),new InicioDiligenciaRequest(EstadoGps.NO_AUTORIZADO,null,null),owner.getUsername());
        assertEquals(EstadoProgramacion.EN_CURSO,visits.findById(first.id()).orElseThrow().getEstadoProgramacion());
        var events=history.findAllByExpedienteIdOrderByFechaHoraAscIdAsc(visits.findById(first.id()).orElseThrow().getExpediente().getId());
        assertTrue(events.stream().anyMatch(h->owner.getId().equals(h.getActor().getId())));
        assertThrows(AccessDeniedException.class,()->inspections.espacio(other.getUsername(),first.id()));
    }
    @Test void archivarYDesactivarMantieneIdentidadAutorHistorialYContrasena() {
        var muni=create("muni-h-",Rol.MUNICIPIO);var owner=create("owner-h-",Rol.FISCALIZADOR);
        owner.getFiscalizador().setMunicipioResponsable(muni);directory.flush();
        String code=expediente(muni,"Historial preservado");String fid=owner.getFiscalizador().getId();String hash=owner.getPasswordHash();
        var visit=cases.programar(code,new ProgramacionRequest(fid,LocalDate.now(ZoneId.of("America/Lima")),LocalTime.NOON,TipoVisita.INOPINADA),muni.getUsername());
        accounts.cambiarEstado(owner.getUsername(),false);
        assertEquals(fid,visits.findById(visit.id()).orElseThrow().getFiscalizador().getId());
        accounts.cambiarEstado(owner.getUsername(),true);
        var result=accounts.eliminarOArchivar(owner.getUsername(),muni.getUsername());
        assertEquals("ARCHIVADA",result.accion());assertEquals(hash,owner.getPasswordHash());assertEquals(fid,owner.getFiscalizador().getId());
        assertTrue(users.existsById(owner.getId()));assertTrue(directory.existsById(fid));assertNotNull(owner.getArchivedAt());
        assertEquals(muni.getId(),visits.findById(visit.id()).orElseThrow().getCreadaPor().getId());
    }
    @Test void noVinculaUnaIdentidadQueYaTieneCuentaAunqueEsteInactiva() {
        var owner=create("owned-",Rol.FISCALIZADOR);accounts.cambiarEstado(owner.getUsername(),false);
        assertThrows(ConflictException.class,()->accounts.crear(new UsuarioRequest("duplicate-"+suffix,"Otro nombre","Test123456",null,Rol.FISCALIZADOR,owner.getFiscalizador().getId())));
    }
    @Test void retirarRolOperativoDesvinculaCuentaYConservaLasAsignaciones() {
        var muni=create("muni-r-",Rol.MUNICIPIO);var owner=create("owner-r-",Rol.FISCALIZADOR);String fid=owner.getFiscalizador().getId();
        owner.getFiscalizador().setMunicipioResponsable(muni);directory.flush();
        String code=expediente(muni,"Asignación histórica");var p=cases.programar(code,new ProgramacionRequest(fid,LocalDate.now(ZoneId.of("America/Lima")),LocalTime.NOON,TipoVisita.INOPINADA),muni.getUsername());
        accounts.asignarRoles(owner.getUsername(),List.of("MUNICIPIO"),null);
        assertNull(owner.getFiscalizador());assertEquals(Rol.MUNICIPIO,owner.getRole());assertEquals(fid,visits.findById(p.id()).orElseThrow().getFiscalizador().getId());
    }
}

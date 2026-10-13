package pe.gob.munisanmiguel.service;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.repository.AppUserRepository;
import pe.gob.munisanmiguel.controller.MunicipalAgendaController;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class MunicipalAccessServiceTest {
    private final MunicipalAccessService access=new MunicipalAccessService(mock(AppUserRepository.class),mock(JdbcTemplate.class));
    private AppUser municipal(long id){var a=new AppUser();a.setId(id);var r=new AppRole();r.setName("MUNICIPIO");a.getRoles().add(r);return a;}
    @Test void responsableGestionaVariosInspectoresSinCompartirIdentidad(){var a=municipal(1);var b=municipal(2);var f1=new Fiscalizador();f1.setMunicipioResponsable(a);var f2=new Fiscalizador();f2.setMunicipioResponsable(a);assertTrue(access.manages(a,f1));assertTrue(access.manages(a,f2));assertThrows(AccessDeniedException.class,()->access.requireAssignment(b,f1));assertNull(a.getFiscalizador());}
    @Test void autorConservaAccesoAlExpedienteYMunicipioAjenoNo(){var a=municipal(1);var b=municipal(2);var e=new ExpedienteFiscalizacion();e.setCreadoPor(a);assertTrue(access.canRead(e,a));assertFalse(access.canRead(e,b));}
    @Test void umbralesMatematicosYEstadosDeCapacidad(){assertEquals("neutro",MunicipalAgendaController.color(0,5));assertEquals("verde",MunicipalAgendaController.color(3,5));assertEquals("amarillo",MunicipalAgendaController.color(4,5));assertEquals("amarillo",MunicipalAgendaController.color(5,5));assertEquals("rojo",MunicipalAgendaController.color(6,5));assertEquals("amarillo",MunicipalAgendaController.color(1,1));assertEquals("rojo",MunicipalAgendaController.color(2,1));assertFalse(MunicipalAgendaController.occupies(EstadoProgramacion.REALIZADA));assertFalse(MunicipalAgendaController.occupies(EstadoProgramacion.CANCELADA));assertFalse(MunicipalAgendaController.occupies(EstadoProgramacion.REPROGRAMADA));}
}

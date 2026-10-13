package pe.gob.munisanmiguel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.repository.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExpedienteFiscalizacionServiceTest {
    private static final java.util.concurrent.atomic.AtomicLong NEXT_PROGRAM_ID=new java.util.concurrent.atomic.AtomicLong(100);
    private ExpedienteFiscalizacionService service(ExpedienteFiscalizacionRepository expedientes,
            ProgramacionFiscalizacionRepository programaciones,ExpedienteHistorialRepository historial,
            AppUserRepository users,FiscalizadorRepository fiscalizadores,SolicitudRepository solicitudes){
        return new ExpedienteFiscalizacionService(expedientes,programaciones,mock(DiligenciaFiscalizacionRepository.class),historial,users,fiscalizadores,solicitudes,new ObjectMapper().findAndRegisterModules(),mock(DocumentoStorageService.class),mock(MunicipalAccessService.class));
    }

    @Test void reprogramarConservaLaVisitaAnteriorYEnlazaLaNueva(){
        var expedientes=mock(ExpedienteFiscalizacionRepository.class);var programs=mock(ProgramacionFiscalizacionRepository.class);
        var history=mock(ExpedienteHistorialRepository.class);var users=mock(AppUserRepository.class);
        var fiscalizadores=mock(FiscalizadorRepository.class);var solicitudes=mock(SolicitudRepository.class);
        var expediente=new ExpedienteFiscalizacion();expediente.setId(9L);expediente.setCodigo("FIS-2026-00009");expediente.setEstado(EstadoExpediente.ABIERTO);
        var fiscalizador=new Fiscalizador();fiscalizador.setId("F-1");fiscalizador.setNombre("Inspector");fiscalizador.setActivo(true);
        var actor=new AppUser();actor.setUsername("municipio");actor.setActivo(true);
        var anterior=new ProgramacionFiscalizacion();anterior.setId(21L);anterior.setExpediente(expediente);anterior.setFiscalizador(fiscalizador);anterior.setEstadoProgramacion(EstadoProgramacion.VENCIDA);
        when(programs.findById(21L)).thenReturn(Optional.of(anterior));when(users.findByUsernameIgnoreCaseAndActivoTrue("municipio")).thenReturn(Optional.of(actor));
        when(fiscalizadores.findById("F-1")).thenReturn(Optional.of(fiscalizador));when(programs.save(any())).thenAnswer(inv->inv.getArgument(0));
        when(history.save(any())).thenAnswer(inv->inv.getArgument(0));when(history.findAllByExpedienteIdOrderByFechaHoraAscIdAsc(9L)).thenReturn(List.of());
        var svc=service(expedientes,programs,history,users,fiscalizadores,solicitudes);
        var nueva=svc.reprogramar(21L,new pe.gob.munisanmiguel.dto.ApiDtos.ReprogramarRequest(
                LocalDate.now(ZoneId.of("America/Lima")).plusDays(2),LocalTime.of(10,30),"Visita anterior vencida",null,TipoVisita.PROGRAMADA),"municipio");

        assertEquals(EstadoProgramacion.REPROGRAMADA,anterior.getEstadoProgramacion());
        assertEquals(EstadoProgramacion.PROGRAMADA.name(),nueva.estado());
        assertEquals(21L,nueva.programacionAnteriorId());
        assertEquals(EstadoExpediente.ABIERTO,expediente.getEstado());
        verify(history,times(2)).save(any(ExpedienteHistorial.class));
    }

    @Test void vencimientoMarcaLaProgramacionYDejaAbiertoElExpediente(){
        var expedientes=mock(ExpedienteFiscalizacionRepository.class);var programs=mock(ProgramacionFiscalizacionRepository.class);
        var history=mock(ExpedienteHistorialRepository.class);var users=mock(AppUserRepository.class);
        var fiscalizadores=mock(FiscalizadorRepository.class);var solicitudes=mock(SolicitudRepository.class);
        var expediente=new ExpedienteFiscalizacion();expediente.setId(3L);expediente.setEstado(EstadoExpediente.ABIERTO);
        var p=new ProgramacionFiscalizacion();p.setId(7L);p.setExpediente(expediente);p.setEstadoProgramacion(EstadoProgramacion.PROGRAMADA);
        p.setFecha(LocalDate.of(2000,1,1));p.setHora(LocalTime.NOON);
        when(programs.findAllByOrderByFechaAscHoraAsc()).thenReturn(List.of(p));
        var svc=service(expedientes,programs,history,users,fiscalizadores,solicitudes);svc.vencerProgramaciones();
        assertEquals(EstadoProgramacion.VENCIDA,p.getEstadoProgramacion());assertEquals(EstadoExpediente.ABIERTO,expediente.getEstado());
        verify(history).save(argThat(h->h.getActorUsername().equals("SISTEMA")&&h.getFechaHora()!=null&&h.getProgramacion()==p));
    }

    @Test void horaProgramadaPasadaNoVenceDuranteElMismoDiaEnLima(){
        var programs=mock(ProgramacionFiscalizacionRepository.class);
        var history=mock(ExpedienteHistorialRepository.class);
        var p=new ProgramacionFiscalizacion();p.setEstadoProgramacion(EstadoProgramacion.PROGRAMADA);
        p.setFecha(LocalDate.now(ZoneId.of("America/Lima")));p.setHora(LocalTime.MIDNIGHT);
        when(programs.findAllByOrderByFechaAscHoraAsc()).thenReturn(List.of(p));
        service(mock(ExpedienteFiscalizacionRepository.class),programs,history,mock(AppUserRepository.class),
                mock(FiscalizadorRepository.class),mock(SolicitudRepository.class)).vencerProgramaciones();
        assertEquals(EstadoProgramacion.PROGRAMADA,p.getEstadoProgramacion());
        verify(history,never()).save(any());
    }

    @Test void jornadaHtmlVaciaPerteneceAlFiscalizadorAutenticado(){
        var expedientes=mock(ExpedienteFiscalizacionRepository.class);var programs=mock(ProgramacionFiscalizacionRepository.class);
        var history=mock(ExpedienteHistorialRepository.class);var users=mock(AppUserRepository.class);
        var fiscalizadores=mock(FiscalizadorRepository.class);var solicitudes=mock(SolicitudRepository.class);
        var fiscalizador=new Fiscalizador();fiscalizador.setId("F-1");fiscalizador.setNombre("Inspector");
        var actor=new AppUser();actor.setUsername("fiscal");actor.setDisplayName("Inspector Uno");actor.setFiscalizador(fiscalizador);
        when(users.findByUsernameIgnoreCaseAndActivoTrue("fiscal")).thenReturn(Optional.of(actor));
        when(programs.findAllByFiscalizadorIdAndEstadoProgramacionInOrderByFechaAscHoraAsc(eq("F-1"),any())).thenReturn(List.of());
        var jornada=service(expedientes,programs,history,users,fiscalizadores,solicitudes)
                .jornadaHtml("fiscal",LocalDate.of(2026,10,8)).content();
        var html=new String(jornada,java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.contains("No hay visitas asignadas"));assertTrue(html.contains("Guía de jornada"));
        verify(programs).findAllByFiscalizadorIdAndEstadoProgramacionInOrderByFechaAscHoraAsc(eq("F-1"),any());
    }

    @Test void jornadaHtmlConservaTarjetasYUnSoloRecorridoSinMapa(){
        var expedientes=mock(ExpedienteFiscalizacionRepository.class);var programs=mock(ProgramacionFiscalizacionRepository.class);
        var history=mock(ExpedienteHistorialRepository.class);var users=mock(AppUserRepository.class);
        var fiscalizadores=mock(FiscalizadorRepository.class);var solicitudes=mock(SolicitudRepository.class);
        var fiscalizador=new Fiscalizador();fiscalizador.setId("F-1");fiscalizador.setNombre("Inspector");
        var actor=new AppUser();actor.setUsername("fiscal");actor.setDisplayName("Inspector & Uno");actor.setFiscalizador(fiscalizador);
        var first=program("FIS-2",LocalTime.of(1,0),"Jr. Dirección 2",null,null,fiscalizador);
        var second=program("FIS-1",LocalTime.of(13,0),"Av. Uno",new java.math.BigDecimal("-15.3977"),new java.math.BigDecimal("-70.1217"),fiscalizador);
        var missing=program("FIS-3",LocalTime.of(16,0),"",null,null,fiscalizador);
        when(users.findByUsernameIgnoreCaseAndActivoTrue("fiscal")).thenReturn(Optional.of(actor));
        when(programs.findAllByFiscalizadorIdAndEstadoProgramacionInOrderByFechaAscHoraAsc(eq("F-1"),any())).thenReturn(List.of(second,missing,first));
        var html=new String(service(expedientes,programs,history,users,fiscalizadores,solicitudes)
                .jornadaHtml("fiscal",LocalDate.of(2026,10,8)).content(),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.indexOf("FIS-2")<html.indexOf("FIS-1"));
        assertTrue(html.contains("1:00 a. m."));assertTrue(html.contains("1:00 p. m."));
        assertTrue(html.contains("data-destination=\"-15.3977,-70.1217\""));
        assertTrue(html.contains("data-destination=\"Jr. Dirección 2\""));
        assertTrue(html.contains("data-destination=\"\""));
        assertTrue(html.contains("Inspector &amp; Uno"));assertTrue(html.contains("Administrado FIS-1 Apellidos"));
        assertTrue(html.contains("Consultar recorrido pendiente"));
        assertTrue(html.contains("stops.length===1"));assertTrue(html.contains("stops.slice(1,-1)"));
        assertTrue(html.contains("cards.filter(c=>c.dataset.attended!=='yes')"));
        assertTrue(html.contains("route.disabled=!stops.length"));
        assertTrue(html.contains("Desmarcar atención local"));
        assertTrue(html.contains("nunca actualizan el sistema"));
        assertFalse(html.contains("Mapa de domicilios"));assertFalse(html.contains("Ver en mapa"));
        assertFalse(html.contains("<svg"));assertFalse(html.contains("tile.openstreetmap.org"));
        assertFalse(html.contains("<script src="));assertFalse(html.contains("fetch("));
        assertTrue(html.length()<60_000);
    }

    @Test void jornadaConUnaVisitaUsaDestinoExactoYNoGps(){
        var programs=mock(ProgramacionFiscalizacionRepository.class);var users=mock(AppUserRepository.class);
        var fiscalizador=new Fiscalizador();fiscalizador.setId("F-1");fiscalizador.setNombre("Inspector");
        var actor=new AppUser();actor.setUsername("fiscal");actor.setDisplayName("Inspector");actor.setFiscalizador(fiscalizador);
        when(users.findByUsernameIgnoreCaseAndActivoTrue("fiscal")).thenReturn(Optional.of(actor));
        when(programs.findAllByFiscalizadorIdAndEstadoProgramacionInOrderByFechaAscHoraAsc(eq("F-1"),any()))
                .thenReturn(List.of(program("FIS-UNO",LocalTime.of(9,0),"Calle Uno",new java.math.BigDecimal("-15.4754"),new java.math.BigDecimal("-70.1285"),fiscalizador)));
        var html=new String(service(mock(ExpedienteFiscalizacionRepository.class),programs,mock(ExpedienteHistorialRepository.class),users,
                mock(FiscalizadorRepository.class),mock(SolicitudRepository.class))
                .jornadaHtml("fiscal",LocalDate.of(2026,10,8)).content(),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.contains("data-destination=\"-15.4754,-70.1285\""));
        assertTrue(html.contains("www.google.com/maps/search/"));
        assertFalse(html.contains("navigator.geolocation"));
        assertFalse(html.contains("local-map"));
    }

    private ProgramacionFiscalizacion program(String code,LocalTime hour,String address,java.math.BigDecimal lat,java.math.BigDecimal lon,Fiscalizador fiscalizador){
        var e=new ExpedienteFiscalizacion();e.setCodigo(code);e.setObjetoFiscalizacion("Objeto "+code);e.setNombreAdministrado("Administrado "+code);e.setAdministradoApellidos("Apellidos");e.setDireccionFiscalizada(address);e.setReferenciaDomicilio("Referencia");e.setLatitud(lat);e.setLongitud(lon);
        var p=new ProgramacionFiscalizacion();p.setId(NEXT_PROGRAM_ID.incrementAndGet());p.setExpediente(e);p.setFiscalizador(fiscalizador);p.setFecha(LocalDate.of(2026,10,8));p.setHora(hour);p.setEstadoProgramacion(EstadoProgramacion.PROGRAMADA);return p;
    }
}

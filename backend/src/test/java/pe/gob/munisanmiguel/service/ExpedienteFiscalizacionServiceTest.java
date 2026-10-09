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
    private ExpedienteFiscalizacionService service(ExpedienteFiscalizacionRepository expedientes,
            ProgramacionFiscalizacionRepository programaciones,ExpedienteHistorialRepository historial,
            AppUserRepository users,FiscalizadorRepository fiscalizadores,SolicitudRepository solicitudes){
        return new ExpedienteFiscalizacionService(expedientes,programaciones,mock(DiligenciaFiscalizacionRepository.class),historial,users,fiscalizadores,solicitudes,new ObjectMapper().findAndRegisterModules(),mock(DocumentoStorageService.class));
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
                LocalDate.of(2026,10,10),LocalTime.of(10,30),"Visita anterior vencida",null,TipoVisita.PROGRAMADA),"municipio");

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
        var html=new String(service(expedientes,programs,history,users,fiscalizadores,solicitudes)
                .jornadaHtml("fiscal",LocalDate.of(2026,10,8)).content(),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.contains("No hay visitas pendientes"));assertFalse(html.contains("<script"));
        verify(programs).findAllByFiscalizadorIdAndEstadoProgramacionInOrderByFechaAscHoraAsc(eq("F-1"),any());
    }

    @Test void jornadaHtmlOrdenaVisitasYResuelveMapaPorCoordenadasODireccion(){
        var expedientes=mock(ExpedienteFiscalizacionRepository.class);var programs=mock(ProgramacionFiscalizacionRepository.class);
        var history=mock(ExpedienteHistorialRepository.class);var users=mock(AppUserRepository.class);
        var fiscalizadores=mock(FiscalizadorRepository.class);var solicitudes=mock(SolicitudRepository.class);
        var fiscalizador=new Fiscalizador();fiscalizador.setId("F-1");fiscalizador.setNombre("Inspector");
        var actor=new AppUser();actor.setUsername("fiscal");actor.setDisplayName("Inspector & Uno");actor.setFiscalizador(fiscalizador);
        var first=program("FIS-2",LocalTime.of(1,0),"Jr. Dirección 2",null,null,fiscalizador);
        var second=program("FIS-1",LocalTime.of(13,0),"Av. Uno",new java.math.BigDecimal("-15.3"),new java.math.BigDecimal("-70.1"),fiscalizador);
        var missing=program("FIS-3",LocalTime.of(16,0),"",null,null,fiscalizador);
        when(users.findByUsernameIgnoreCaseAndActivoTrue("fiscal")).thenReturn(Optional.of(actor));
        when(programs.findAllByFiscalizadorIdAndEstadoProgramacionInOrderByFechaAscHoraAsc(eq("F-1"),any())).thenReturn(List.of(second,missing,first));
        var html=new String(service(expedientes,programs,history,users,fiscalizadores,solicitudes)
                .jornadaHtml("fiscal",LocalDate.of(2026,10,8)).content(),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.indexOf("FIS-2")<html.indexOf("FIS-1"));assertTrue(html.contains("1:00 a. m."));assertTrue(html.contains("1:00 p. m."));
        assertTrue(html.contains("query=-15.3,-70.1"));assertTrue(html.contains("Jr.+Direcci%C3%B3n+2"));assertTrue(html.contains("Inspector &amp; Uno"));
        assertTrue(html.contains("Sin ubicación suficiente"));
        assertTrue(html.contains("data-toggle"));
        assertTrue(html.contains("id=\"local-map\""));
        assertTrue(html.contains("route-link"));
        assertTrue(html.contains("no registra diligencias, actas ni cambios"));
    }

    private ProgramacionFiscalizacion program(String code,LocalTime hour,String address,java.math.BigDecimal lat,java.math.BigDecimal lon,Fiscalizador fiscalizador){
        var e=new ExpedienteFiscalizacion();e.setCodigo(code);e.setObjetoFiscalizacion("Objeto "+code);e.setDireccionFiscalizada(address);e.setReferenciaDomicilio("Referencia");e.setLatitud(lat);e.setLongitud(lon);
        var p=new ProgramacionFiscalizacion();p.setExpediente(e);p.setFiscalizador(fiscalizador);p.setFecha(LocalDate.of(2026,10,8));p.setHora(hour);p.setEstadoProgramacion(EstadoProgramacion.PROGRAMADA);return p;
    }
}

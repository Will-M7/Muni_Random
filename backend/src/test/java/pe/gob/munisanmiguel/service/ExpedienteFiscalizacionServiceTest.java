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
}

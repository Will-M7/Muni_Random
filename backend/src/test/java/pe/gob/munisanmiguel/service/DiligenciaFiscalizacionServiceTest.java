package pe.gob.munisanmiguel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import pe.gob.munisanmiguel.dto.ApiDtos.InicioDiligenciaRequest;
import pe.gob.munisanmiguel.dto.ApiDtos.NoRealizadaRequest;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.exception.ConflictException;
import pe.gob.munisanmiguel.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DiligenciaFiscalizacionServiceTest {
    private Fixture fixture(){
        var f=new Fixture();
        f.fiscalizador.setId("F-1");f.fiscalizador.setNombre("Fiscalizador sintético");f.fiscalizador.setActivo(true);
        f.actor.setUsername("fiscalizador");f.actor.setCargo("Fiscalizador de campo");f.actor.setActivo(true);f.actor.setFiscalizador(f.fiscalizador);
        f.expediente.setId(10L);f.expediente.setCodigo("FIS-TEST");f.expediente.setOrigen(OrigenFiscalizacion.INICIATIVA_MUNICIPAL);
        f.expediente.setObjetoFiscalizacion("Verificar hechos de prueba");f.expediente.setEstado(EstadoExpediente.ABIERTO);
        f.expediente.setDireccionFiscalizada("Dirección ficticia de prueba");f.programacion.setId(20L);f.programacion.setExpediente(f.expediente);
        f.programacion.setFiscalizador(f.fiscalizador);f.programacion.setFecha(LocalDate.now().plusDays(1));f.programacion.setHora(LocalTime.of(10,0));
        f.programacion.setTipoVisita(TipoVisita.PROGRAMADA);f.programacion.setEstadoProgramacion(EstadoProgramacion.PROGRAMADA);
        when(f.users.findByUsernameIgnoreCaseAndActivoTrue("fiscalizador")).thenReturn(Optional.of(f.actor));
        when(f.users.findByUsernameIgnoreCaseAndActivoTrue("otro")).thenReturn(Optional.of(new AppUser()));
        when(f.programs.findByIdForUpdate(20L)).thenReturn(Optional.of(f.programacion));
        when(f.history.save(any())).thenAnswer(i->i.getArgument(0));
        when(f.diligences.findByProgramacionId(20L)).thenReturn(Optional.empty());
        when(f.diligences.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        when(f.participants.save(any())).thenAnswer(i->i.getArgument(0));
        when(f.participants.findAllByDiligenciaIdOrderByCreadoEnAscIdAsc(any())).thenReturn(List.of());
        when(f.evidences.findAllByDiligenciaIdOrderByFechaHoraAscIdAsc(any())).thenReturn(List.of());
        f.service=new DiligenciaFiscalizacionService(f.programs,f.diligences,f.participants,f.evidences,f.actas,f.users,
                f.history,mock(FiscalizacionFileStorageService.class),mock(DocumentoStorageService.class),
                new ActaPdfService(),new ObjectMapper().findAndRegisterModules(),f.revisions);
        return f;
    }

    @Test void iniciarTareaPropiaGuardaGpsYAgregaAlResponsable(){
        var f=fixture();
        f.service.iniciar(20L,new InicioDiligenciaRequest(EstadoGps.DISPONIBLE,new BigDecimal("-12.0772610"),new BigDecimal("-77.0928690")),"fiscalizador");
        assertEquals(EstadoProgramacion.EN_CURSO,f.programacion.getEstadoProgramacion());
        assertEquals(EstadoExpediente.EN_FISCALIZACION,f.expediente.getEstado());
        var captor=org.mockito.ArgumentCaptor.forClass(DiligenciaFiscalizacion.class);
        verify(f.diligences).saveAndFlush(captor.capture());
        assertEquals(EstadoDiligencia.INICIADA,captor.getValue().getEstado());
        assertEquals(EstadoGps.DISPONIBLE,captor.getValue().getGpsInicioEstado());
        assertEquals(new BigDecimal("-12.0772610"),captor.getValue().getLatitudInicio());
        verify(f.participants).save(argThat(p->p.getTipoParticipante()==TipoParticipante.FISCALIZADOR_RESPONSABLE));
        verify(f.history,times(2)).save(any());
    }

    @Test void otroFiscalizadorRecibe403YNoIniciaLaProgramacion(){
        var f=fixture();
        assertThrows(AccessDeniedException.class,()->f.service.iniciar(20L,new InicioDiligenciaRequest(EstadoGps.NO_AUTORIZADO,null,null),"otro"));
        assertEquals(EstadoProgramacion.PROGRAMADA,f.programacion.getEstadoProgramacion());
        verify(f.diligences,never()).saveAndFlush(any());
    }

    @Test void gpsDenegadoNoGuardaCoordenadasInventadas(){
        var f=fixture();
        f.service.iniciar(20L,new InicioDiligenciaRequest(EstadoGps.NO_AUTORIZADO,null,null),"fiscalizador");
        var captor=org.mockito.ArgumentCaptor.forClass(DiligenciaFiscalizacion.class);verify(f.diligences).saveAndFlush(captor.capture());
        assertEquals(EstadoGps.NO_AUTORIZADO,captor.getValue().getGpsInicioEstado());
        assertNull(captor.getValue().getLatitudInicio());assertNull(captor.getValue().getLongitudInicio());
    }

    @Test void evitaUnSegundoInicioSobreLaProgramacionEnCurso(){
        var f=fixture();f.programacion.setEstadoProgramacion(EstadoProgramacion.EN_CURSO);
        assertThrows(ConflictException.class,()->f.service.iniciar(20L,new InicioDiligenciaRequest(EstadoGps.NO_DISPONIBLE,null,null),"fiscalizador"));
        verify(f.diligences,never()).saveAndFlush(any());
    }

    @Test void programacionPasadaSeMarcaVencidaSinCerrarElExpediente(){
        var f=fixture();f.programacion.setFecha(LocalDate.of(2000,1,1));f.programacion.setHora(LocalTime.NOON);
        var workspace=f.service.iniciar(20L,new InicioDiligenciaRequest(EstadoGps.NO_DISPONIBLE,null,null),"fiscalizador");
        assertEquals("VENCIDA",workspace.estadoProgramacion());
        assertEquals(EstadoExpediente.ABIERTO,f.expediente.getEstado());
        assertNull(workspace.id());verify(f.diligences,never()).saveAndFlush(any());
        verify(f.history).save(argThat(h->h.getTipoEvento().equals("PROGRAMACION_VENCIDA")&&h.getActorUsername().equals("fiscalizador")));
    }

    @Test void noRealizadaAceptaMotivoTipadoSinDetalleYDejaExpedienteAbierto(){
        var f=fixture();
        var result=f.service.noRealizada(20L,new NoRealizadaRequest(MotivoNoRealizada.ADMINISTRADO_AUSENTE,null,"Sin persona presente",null,EstadoGps.NO_AUTORIZADO,null,null),"fiscalizador");
        assertEquals("NO_REALIZADA",result.estadoProgramacion());assertEquals("NO_REALIZADA",result.estadoDiligencia());
        assertEquals(EstadoExpediente.ABIERTO,f.expediente.getEstado());
        verify(f.history).save(argThat(h->h.getTipoEvento().equals("DILIGENCIA_NO_REALIZADA")&&h.getFechaHora()!=null));
    }

    private static class Fixture {
        final ProgramacionFiscalizacionRepository programs=mock(ProgramacionFiscalizacionRepository.class);
        final DiligenciaFiscalizacionRepository diligences=mock(DiligenciaFiscalizacionRepository.class);
        final DiligenciaParticipanteRepository participants=mock(DiligenciaParticipanteRepository.class);
        final EvidenciaFiscalizacionRepository evidences=mock(EvidenciaFiscalizacionRepository.class);
        final ActaFiscalizacionRepository actas=mock(ActaFiscalizacionRepository.class);
        final AppUserRepository users=mock(AppUserRepository.class);
        final ExpedienteHistorialRepository history=mock(ExpedienteHistorialRepository.class);
        final RevisionFiscalizacionRepository revisions=mock(RevisionFiscalizacionRepository.class);
        final Fiscalizador fiscalizador=new Fiscalizador();final AppUser actor=new AppUser();
        final ExpedienteFiscalizacion expediente=new ExpedienteFiscalizacion();final ProgramacionFiscalizacion programacion=new ProgramacionFiscalizacion();
        DiligenciaFiscalizacionService service;
    }
}

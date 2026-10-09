package pe.gob.munisanmiguel.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.gob.munisanmiguel.dto.ApiDtos.*;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.exception.ConflictException;
import pe.gob.munisanmiguel.repository.*;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AltaFiscalizacionServiceTest {
    @Test void altaSinProgramacionPermiteCompletarlaDesdeDetalle(){
        var f=new Fixture();var request=f.request(null);
        var result=f.service.crear(request,List.of(),"municipio");
        assertSame(f.response,result);
        assertEquals("Mesa de partes",f.expediente.getDependenciaProcedencia());
        assertEquals("MEM-21",f.expediente.getReferenciaSolicitud());
        verify(f.expedientes,never()).programar(anyString(),any(),anyString());
    }

    @Test void errorDeProgramacionDetieneAltaAntesDeGuardarDocumentos(){
        var f=new Fixture();var programacion=new ProgramacionRequest("F-1",LocalDate.now().plusDays(1),LocalTime.of(10,0),TipoVisita.PROGRAMADA);
        doThrow(new ConflictException("Fiscalizador ocupado")).when(f.expedientes).programar(eq("FIS-TEST"),eq(programacion),eq("municipio"));
        assertThrows(ConflictException.class,()->f.service.crear(f.request(programacion),
                List.of(new MockMultipartFile("documentos","orden.pdf","application/pdf",new byte[]{1})),"municipio"));
        verifyNoInteractions(f.documentos,f.storage);
    }

    @Test void solicitudExternaRepetidaNoAbreOtroExpediente(){
        var f=new Fixture();
        when(f.repository.existsByDependenciaProcedenciaIgnoreCaseAndReferenciaSolicitudIgnoreCase("Mesa de partes","MEM-21"))
                .thenReturn(true);
        assertThrows(ConflictException.class,()->f.service.crear(f.request(null),List.of(),"municipio"));
        verify(f.expedientes,never()).crear(any(),anyString());
    }

    @Test void errorDelSegundoArchivoLimpiaElPrimeroAlRevertirTransaccion(){
        var f=new Fixture();var user=new AppUser();user.setUsername("municipio");
        when(f.users.findByUsernameIgnoreCaseAndActivoTrue("municipio")).thenReturn(Optional.of(user));
        var first=new MockMultipartFile("documentos","primero.pdf","application/pdf",new byte[]{1});
        var second=new MockMultipartFile("documentos","segundo.pdf","application/pdf",new byte[]{2});
        when(f.storage.storeEvidence(first,false)).thenReturn(new FiscalizacionFileStorageService.StoredFile("primero.pdf","uuid.pdf","application/pdf",1));
        when(f.storage.storeEvidence(second,false)).thenThrow(new ConflictException("Archivo inválido"));
        TransactionSynchronizationManager.initSynchronization();
        try{
            assertThrows(ConflictException.class,()->f.service.crear(f.request(null),List.of(first,second),"municipio"));
            for(var callback:TransactionSynchronizationManager.getSynchronizations())
                callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(f.storage).delete("uuid.pdf");
            verify(f.documentos).save(any(ExpedienteDocumento.class));
        }finally{TransactionSynchronizationManager.clearSynchronization();}
    }

    @Test void adjuntoPosteriorRechazaExpedienteCerrado(){
        var f=new Fixture();f.expediente.setEstado(EstadoExpediente.CERRADO);
        assertThrows(ConflictException.class,()->f.service.adjuntar("FIS-TEST",List.of(new MockMultipartFile("documentos","x.pdf","application/pdf",new byte[]{1})),"municipio"));
        verifyNoInteractions(f.storage,f.documentos);
    }

    @Test void adjuntoPosteriorRegistraAutorYNoSobrescribe(){
        var f=new Fixture();f.expediente.setEstado(EstadoExpediente.ABIERTO);
        var user=new AppUser();user.setUsername("municipio");
        when(f.users.findByUsernameIgnoreCaseAndActivoTrue("municipio")).thenReturn(Optional.of(user));
        var file=new MockMultipartFile("documentos","nuevo.pdf","application/pdf",new byte[]{1});
        when(f.storage.storeEvidence(file,false)).thenReturn(new FiscalizacionFileStorageService.StoredFile("nuevo.pdf","uuid.pdf","application/pdf",1));
        TransactionSynchronizationManager.initSynchronization();
        try{
            f.service.adjuntar("FIS-TEST",List.of(file),"municipio");
            verify(f.documentos).save(argThat(d->d.getAdjuntadoPor()==user&&d.getAdjuntadoEn()!=null&&"uuid.pdf".equals(d.getNombreInterno())));
        }finally{TransactionSynchronizationManager.clearSynchronization();}
    }

    private static class Fixture {
        final ExpedienteFiscalizacionService expedientes=mock(ExpedienteFiscalizacionService.class);
        final ExpedienteFiscalizacionRepository repository=mock(ExpedienteFiscalizacionRepository.class);
        final ExpedienteDocumentoRepository documentos=mock(ExpedienteDocumentoRepository.class);
        final FiscalizacionFileStorageService storage=mock(FiscalizacionFileStorageService.class);
        final AppUserRepository users=mock(AppUserRepository.class);
        final ExpedienteFiscalizacion expediente=new ExpedienteFiscalizacion();
        final ExpedienteResponse response=new ExpedienteResponse(null,"FIS-TEST",null,null,null,null,null,null,
                null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,List.of(),List.of());
        final AltaFiscalizacionService service=new AltaFiscalizacionService(expedientes,repository,documentos,storage,users);
        Fixture(){when(expedientes.crear(any(),eq("municipio"))).thenReturn(response);
            when(repository.findByCodigo("FIS-TEST")).thenReturn(Optional.of(expediente));
            when(expedientes.obtener("FIS-TEST")).thenReturn(response);}
        CrearFiscalizacionRequest request(ProgramacionRequest programacion){var data=new ExpedienteRequest(OrigenFiscalizacion.SOLICITUD,
                null,"Verificar predio",null,null,null,null,null,"Jr. Prueba",null,null,null,null,null);
            return new CrearFiscalizacionRequest(data,"Mesa de partes","MEM-21",programacion);}
    }
}

package pe.gob.munisanmiguel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.gob.munisanmiguel.dto.ApiDtos.ConcluirRevisionRequest;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.repository.*;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RevisionFiscalizacionConclusionTest {
    private static final long REVISION_ID = 51L;
    private static final long EXPEDIENTE_ID = 41L;
    private static final String MUNICIPIO = "f3b_test_municipio";

    private RevisionFiscalizacionRepository revisions;
    private SolicitudAclaracionFiscalizacionRepository aclaraciones;
    private DiligenciaFiscalizacionRepository diligencias;
    private ExpedienteFiscalizacionRepository expedientes;
    private AppUserRepository users;
    private ExpedienteHistorialRepository historial;
    private ProgramacionFiscalizacionRepository programaciones;
    private EvidenciaFiscalizacionRepository evidencias;
    private FiscalizadorRepository fiscalizadores;
    private FiscalizacionFileStorageService storage;
    private RevisionFiscalizacionService service;
    private RevisionFiscalizacion revision;
    private ExpedienteFiscalizacion expediente;
    private List<ExpedienteHistorial> eventos;

    @BeforeEach
    void setUp() {
        revisions=mock(RevisionFiscalizacionRepository.class); aclaraciones=mock(SolicitudAclaracionFiscalizacionRepository.class);
        diligencias=mock(DiligenciaFiscalizacionRepository.class); expedientes=mock(ExpedienteFiscalizacionRepository.class);
        users=mock(AppUserRepository.class); historial=mock(ExpedienteHistorialRepository.class);
        programaciones=mock(ProgramacionFiscalizacionRepository.class); evidencias=mock(EvidenciaFiscalizacionRepository.class);
        fiscalizadores=mock(FiscalizadorRepository.class); storage=mock(FiscalizacionFileStorageService.class);
        service=new RevisionFiscalizacionService(revisions,aclaraciones,diligencias,expedientes,users,historial,programaciones,evidencias,
                fiscalizadores,storage,new ObjectMapper());

        expediente=new ExpedienteFiscalizacion(); expediente.setId(EXPEDIENTE_ID); expediente.setCodigo("FIS-2026-00041");
        expediente.setOrigen(OrigenFiscalizacion.INICIATIVA_MUNICIPAL); expediente.setObjetoFiscalizacion("Objeto sintético");
        expediente.setEstado(EstadoExpediente.PENDIENTE_REVISION);
        Fiscalizador fiscalizador=new Fiscalizador(); fiscalizador.setId("FIS-001"); fiscalizador.setNombre("Fiscalizador sintético");
        ProgramacionFiscalizacion programacion=new ProgramacionFiscalizacion(); programacion.setFecha(LocalDate.now().plusDays(1));
        programacion.setHora(LocalTime.NOON); programacion.setTipoVisita(TipoVisita.PROGRAMADA); programacion.setEstadoProgramacion(EstadoProgramacion.REALIZADA);
        programacion.setFiscalizador(fiscalizador); programacion.setExpediente(expediente);
        DiligenciaFiscalizacion diligencia=new DiligenciaFiscalizacion(); diligencia.setId(61L); diligencia.setExpediente(expediente);
        diligencia.setProgramacion(programacion); diligencia.setFiscalizadorResponsable(fiscalizador); diligencia.setEstado(EstadoDiligencia.FINALIZADA);
        diligencia.setSituacionPersona(SituacionPersona.IDENTIFICADA); diligencia.setPersonaNombres("Persona");
        diligencia.setPersonaApellidos("Ficticia"); diligencia.setPersonaTipoDocumento("DNI"); diligencia.setPersonaNumeroDocumento("00000000");
        diligencia.setPersonaCondicion("Administrado sintético"); diligencia.setFechaInicio(LocalDateTime.now().minusHours(1));
        diligencia.setFechaCierre(LocalDateTime.now());
        ActaFiscalizacion acta=new ActaFiscalizacion(); acta.setId(71L); acta.setCodigo("ACT-2026-00071"); acta.setContenidoJson("{}");
        acta.setFirmaFiscalizadoEstado(EstadoFirmaActa.FIRMA_PENDIENTE); acta.setGeneradoEn(LocalDateTime.now());
        acta.setDiligencia(diligencia); diligencia.setActa(acta);
        RevisionFiscalizacion rev=new RevisionFiscalizacion(); rev.setId(REVISION_ID); rev.setExpediente(expediente); rev.setDiligencia(diligencia);
        rev.setActa(acta); rev.setEstadoRevision(EstadoRevision.EN_REVISION); rev.setCreadoEn(LocalDateTime.now()); rev.setActualizadoEn(LocalDateTime.now());
        AppUser actor=new AppUser(); actor.setUsername(MUNICIPIO); actor.setDisplayName("MUNICIPIO sintético"); actor.setActivo(true);
        rev.setRevisor(actor); revision=rev;

        when(revisions.findLocked(REVISION_ID)).thenReturn(Optional.of(revision));
        when(users.findByUsernameIgnoreCaseAndActivoTrue(MUNICIPIO)).thenReturn(Optional.of(actor));
        when(aclaraciones.existsByRevisionIdAndEstado(REVISION_ID,EstadoAclaracion.PENDIENTE)).thenReturn(false);
        when(diligencias.findAllByExpedienteIdOrderByFechaInicioDesc(EXPEDIENTE_ID)).thenReturn(List.of());
        when(programaciones.findAllByExpedienteIdOrderByIdAsc(EXPEDIENTE_ID)).thenReturn(List.of());
        when(programaciones.findAllByRevisionOrigen_IdOrderByIdAsc(REVISION_ID)).thenReturn(List.of());
        when(storage.storeGeneratedPdf(any(),anyString())).thenAnswer(a -> new FiscalizacionFileStorageService.StoredFile(a.getArgument(1),UUID.randomUUID()+".pdf","application/pdf",1024));
        eventos=new ArrayList<>(); when(historial.save(any())).thenAnswer(a->{ExpedienteHistorial h=a.getArgument(0);eventos.add(h);return h;});
    }

    @Test void conformidadRequiereFundamentoYNoAdmiteSeguimiento() {
        var validator=jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        assertTrue(validator.validate(request(ConclusionFiscalizacion.CONFORMIDAD,"",null,null,false,false,null)).stream()
                .anyMatch(v->v.getPropertyPath().toString().equals("fundamentoConclusion")));
        reset(revisions); when(revisions.findLocked(REVISION_ID)).thenReturn(Optional.of(revision));
        assertThrows(RuntimeException.class,()->service.concluir(REVISION_ID,request(ConclusionFiscalizacion.CONFORMIDAD,"Fundamento",null,null,true,false,null),MUNICIPIO));
        assertEquals(EstadoExpediente.PENDIENTE_REVISION,expediente.getEstado());
    }

    @Test void conformidadConFundamentoCierraSiNoHayPendientes() {
        var result=service.concluir(REVISION_ID,request(ConclusionFiscalizacion.CONFORMIDAD,"Verificación conforme.",null,null,false,false,null),MUNICIPIO);
        assertEquals(ConclusionFiscalizacion.CONFORMIDAD,revision.getConclusion());
        assertFalse(revision.isRequiereSeguimiento()); assertEquals(EstadoExpediente.CERRADO,expediente.getEstado());
        assertTrue(eventos.stream().anyMatch(e->e.getTipoEvento().equals("EXPEDIENTE_CERRADO")));
        assertEquals("CONFORMIDAD",((Map<?,?>)result.get("revision")).get("conclusion"));
    }

    @Test void recomendacionMejorasPuedeCerrarOSolicitarSeguimiento() {
        service.concluir(REVISION_ID,request(ConclusionFiscalizacion.RECOMENDACION_MEJORAS_CORRECCIONES,"Fundamento.","Mejora sintética.","30 días",false,false,null),MUNICIPIO);
        assertEquals(EstadoExpediente.CERRADO,expediente.getEstado());
        setUp();
        service.concluir(REVISION_ID,request(ConclusionFiscalizacion.RECOMENDACION_MEJORAS_CORRECCIONES,"Fundamento.","Mejora sintética.","30 días",true,false,null),MUNICIPIO);
        assertEquals(EstadoExpediente.EN_SEGUIMIENTO,expediente.getEstado());
        assertFalse(eventos.stream().anyMatch(e->e.getTipoEvento().equals("EXPEDIENTE_CERRADO")));
    }

    @Test void advertenciaExigeDescripcionYNoGeneraSancionAutomatica() {
        assertThrows(RuntimeException.class,()->service.concluir(REVISION_ID,request(ConclusionFiscalizacion.ADVERTENCIA,"Fundamento.",null,null,false,false,null),MUNICIPIO));
        setUp();
        service.concluir(REVISION_ID,request(ConclusionFiscalizacion.ADVERTENCIA,"Fundamento.","Advertencia sintética registrada por MUNICIPIO.",null,false,false,null),MUNICIPIO);
        assertEquals("Advertencia sintética registrada por MUNICIPIO.",revision.getDescripcionConclusion());
        assertEquals(List.of("REVISION_FINALIZADA","CONCLUSION_REGISTRADA","EXPEDIENTE_CERRADO","INFORME_CONCLUSION_GENERADO"),eventos.stream().map(ExpedienteHistorial::getTipoEvento).toList());
        setUp();
        service.concluir(REVISION_ID,request(ConclusionFiscalizacion.ADVERTENCIA,"Fundamento.","Advertencia sintética con seguimiento.",null,true,false,null),MUNICIPIO);
        assertEquals(EstadoExpediente.EN_SEGUIMIENTO,expediente.getEstado());
        assertFalse(eventos.stream().anyMatch(e->e.getTipoEvento().equals("EXPEDIENTE_CERRADO")));
    }

    @Test void recomiendaInicioProcedimientoExigeDerivacionYSoloRegistraRecomendacion() {
        assertThrows(RuntimeException.class,()->service.concluir(REVISION_ID,request(ConclusionFiscalizacion.RECOMIENDA_INICIO_PROCEDIMIENTO,"Fundamento.",null,null,false,false,null),MUNICIPIO));
        setUp();
        service.concluir(REVISION_ID,request(ConclusionFiscalizacion.RECOMIENDA_INICIO_PROCEDIMIENTO,"Fundamento.",null,null,false,true,null),MUNICIPIO);
        assertTrue(revision.isRequiereDerivacion());
        assertTrue(eventos.stream().anyMatch(e->e.getTipoEvento().equals("DERIVACION_RECOMENDADA")));
        assertFalse(eventos.stream().anyMatch(e->e.getTipoEvento().toLowerCase(Locale.ROOT).contains("multa")
                ||e.getTipoEvento().toLowerCase(Locale.ROOT).contains("sancion")||e.getTipoEvento().toLowerCase(Locale.ROOT).contains("responsabilidad")
                ||e.getTipoEvento().toLowerCase(Locale.ROOT).contains("procedimiento")));
        assertEquals(EstadoExpediente.CERRADO,expediente.getEstado());
    }

    @Test void medidaCorrectivaRequiereDescripcionYPersisteLiteralMunicipal() {
        assertThrows(RuntimeException.class,()->service.concluir(REVISION_ID,request(ConclusionFiscalizacion.MEDIDA_CORRECTIVA,"Fundamento.",null,null,true,false,null),MUNICIPIO));
        setUp();
        String medida="Instalar señal sintética de prueba en el acceso indicado.";
        service.concluir(REVISION_ID,request(ConclusionFiscalizacion.MEDIDA_CORRECTIVA,"Fundamento de medida.",medida,"20 días",true,false,null),MUNICIPIO);
        assertEquals(medida,revision.getDescripcionConclusion()); assertEquals(EstadoExpediente.EN_SEGUIMIENTO,expediente.getEstado());
    }

    @Test void otraExigeDenominacionYDescripcion() {
        assertThrows(RuntimeException.class,()->service.concluir(REVISION_ID,request(ConclusionFiscalizacion.OTRA,"Fundamento.","Detalle",null,false,false,null),MUNICIPIO));
        setUp();
        assertThrows(RuntimeException.class,()->service.concluir(REVISION_ID,request(ConclusionFiscalizacion.OTRA,"Fundamento.",null,null,false,false,"Conclusión especial"),MUNICIPIO));
        setUp();
        service.concluir(REVISION_ID,request(ConclusionFiscalizacion.OTRA,"Fundamento.","Detalle sintético.",null,false,false,"Conclusión especial"),MUNICIPIO);
        assertEquals("Conclusión especial",revision.getDenominacionOtra());
    }

    private ConcluirRevisionRequest request(ConclusionFiscalizacion conclusion,String fundamento,String descripcion,String plazo,
            boolean seguimiento,boolean derivacion,String otra) {
        return new ConcluirRevisionRequest(conclusion,fundamento,null,descripcion,plazo,seguimiento,derivacion,otra,null);
    }
}

package pe.gob.munisanmiguel.service;

import pe.gob.munisanmiguel.dto.ApiDtos.EstadoRequest;
import pe.gob.munisanmiguel.entity.EstadoSolicitud;
import pe.gob.munisanmiguel.entity.Solicitud;
import pe.gob.munisanmiguel.mapper.SolicitudMapper;
import pe.gob.munisanmiguel.repository.FiscalizadorRepository;
import pe.gob.munisanmiguel.repository.SolicitudRepository;
import pe.gob.munisanmiguel.service.DocumentoStorageService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import pe.gob.munisanmiguel.entity.ResultadoFiscalizacion;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SolicitudServiceTest {
    @Test
    void soloExpiraLaVisitaVencidaPendienteYRegistraMotivo() {
        SolicitudRepository solicitudes = mock(SolicitudRepository.class);
        var servicio = new SolicitudService(solicitudes, mock(FiscalizadorRepository.class),
                mock(SolicitudMapper.class), mock(DocumentoStorageService.class));
        LocalDate fechaPasada = LocalDate.of(2026, 9, 26);
        Solicitud vencida = solicitudConResultado(fechaPasada, ResultadoFiscalizacion.PENDIENTE);
        vencida.setEstado(EstadoSolicitud.OBSERVADA);
        Solicitud realizadaUno = solicitudConResultado(fechaPasada, ResultadoFiscalizacion.REALIZADA);
        Solicitud realizadaDos = solicitudConResultado(fechaPasada, ResultadoFiscalizacion.REALIZADA);
        Solicitud noRealizada = solicitudConResultado(fechaPasada, ResultadoFiscalizacion.NO_REALIZADA);
        when(solicitudes.vencidasPendientes(List.of(EstadoSolicitud.EN_ESPERA, EstadoSolicitud.OBSERVADA), ResultadoFiscalizacion.PENDIENTE,
                LocalDate.of(2026, 9, 29), LocalTime.NOON)).thenReturn(List.of(vencida));

        servicio.expirarPendientes(LocalDateTime.of(2026, 9, 29, 12, 0));

        assertEquals(EstadoSolicitud.EXPIRADO, vencida.getEstado());
        assertEquals(ResultadoFiscalizacion.PENDIENTE, vencida.getResultadoFiscalizacion());
        assertEquals(1, vencida.getHistorialCambios().size());
        org.junit.jupiter.api.Assertions.assertTrue(vencida.getHistorialCambios().getFirst().getNota().contains("2026-09-26 10:30"));
        for (Solicitud cerrada : List.of(realizadaUno, realizadaDos, noRealizada)) {
            assertEquals(EstadoSolicitud.EN_ESPERA, cerrada.getEstado());
            assertEquals(0, cerrada.getHistorialCambios().size());
            verify(solicitudes, never()).save(cerrada);
        }
        verify(solicitudes).save(vencida);
    }

    private Solicitud solicitudConResultado(LocalDate fecha, ResultadoFiscalizacion resultado) {
        Solicitud solicitud = new Solicitud();
        solicitud.setEstado(EstadoSolicitud.EN_ESPERA);
        solicitud.setFechaFiscalizacion(fecha);
        solicitud.setHoraFiscalizacion(LocalTime.of(10, 30));
        solicitud.setResultadoFiscalizacion(resultado);
        return solicitud;
    }
    @Test
    void cambioDeEstadoAgregaHistorialEnLaMismaOperacion() {
        SolicitudRepository solicitudes = mock(SolicitudRepository.class);
        FiscalizadorRepository fiscalizadores = mock(FiscalizadorRepository.class);
        SolicitudMapper mapper = mock(SolicitudMapper.class);
        Solicitud solicitud = new Solicitud();
        solicitud.setCodigo("SM-2026-00999");
        solicitud.setEstado(EstadoSolicitud.EN_ESPERA);
        when(solicitudes.findByCodigo(solicitud.getCodigo())).thenReturn(Optional.of(solicitud));
        when(mapper.toResponse(any(Solicitud.class))).thenReturn(null);

        SolicitudService service = new SolicitudService(solicitudes, fiscalizadores, mapper, mock(DocumentoStorageService.class));
        service.cambiarEstado(solicitud.getCodigo(), new EstadoRequest("VERIFICADO", "Visita conforme", null));

        assertEquals(EstadoSolicitud.VERIFICADO, solicitud.getEstado());
        assertEquals(1, solicitud.getHistorialCambios().size());
        assertEquals(EstadoSolicitud.VERIFICADO, solicitud.getHistorialCambios().getFirst().getEstado());
        verify(solicitudes, atLeastOnce()).save(solicitud);
    }

    @Test
    void listaSolicitudesRespetaOrdenDescendenteEntregadoPorRepositorio() {
        SolicitudRepository solicitudes = mock(SolicitudRepository.class);
        FiscalizadorRepository fiscalizadores = mock(FiscalizadorRepository.class);
        SolicitudMapper mapper = mock(SolicitudMapper.class);
        Solicitud anterior = new Solicitud();
        anterior.setCodigo("SM-2026-00001");
        Solicitud reciente = new Solicitud();
        reciente.setCodigo("SM-2026-00002");
        when(solicitudes.findAllByOrderByFechaRegistroDescIdDesc()).thenReturn(List.of(reciente, anterior));
        when(mapper.toResponse(any(Solicitud.class))).thenReturn(null);

        SolicitudService service = new SolicitudService(solicitudes, fiscalizadores, mapper, mock(DocumentoStorageService.class));
        service.buscar(null, null, null, null);

        InOrder orden = inOrder(mapper);
        orden.verify(mapper).toResponse(reciente);
        orden.verify(mapper).toResponse(anterior);
    }

    @Test
    void programacionFiltraPorFechaYConservaOrdenDelRepositorio() {
        SolicitudRepository solicitudes = mock(SolicitudRepository.class);
        FiscalizadorRepository fiscalizadores = mock(FiscalizadorRepository.class);
        SolicitudMapper mapper = mock(SolicitudMapper.class);
        Solicitud reciente = new Solicitud(); reciente.setCodigo("SM-2026-00002"); reciente.setNombres("Ana"); reciente.setApellidos("Pérez"); reciente.setEstado(EstadoSolicitud.EN_ESPERA); reciente.setHoraFiscalizacion(java.time.LocalTime.of(10, 30));
        when(solicitudes.findAllByFechaFiscalizacionAndHoraFiscalizacionIsNotNullOrderByHoraFiscalizacionAsc(java.time.LocalDate.of(2026, 10, 1))).thenReturn(List.of(reciente));
        var result = new SolicitudService(solicitudes, fiscalizadores, mapper, mock(DocumentoStorageService.class)).programacion(java.time.LocalDate.of(2026, 10, 1));
        assertEquals("SM-2026-00002", result.getFirst().codigo());
        assertEquals(java.time.LocalTime.of(10, 30), result.getFirst().horaFiscalizacion());
    }
}

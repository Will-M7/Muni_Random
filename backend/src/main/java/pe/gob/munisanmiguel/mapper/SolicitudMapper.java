package pe.gob.munisanmiguel.mapper;

import pe.gob.munisanmiguel.dto.ApiDtos.*;
import pe.gob.munisanmiguel.entity.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class SolicitudMapper {
    public SolicitudResponse toResponse(Solicitud s) {
        var historial = s.getHistorialCambios().stream().map(h -> new HistorialResponse(h.getFecha().toString(), h.getEstado().toFrontend(), h.getNota())).toList();
        return new SolicitudResponse(s.getCodigo(), s.getNombres(), s.getApellidos(), s.nombreCompleto(), s.getDni(), s.getTelefono(),
                s.getDireccion(), s.getReferencia(), s.getDocumento(), s.getDocumentoTamano(), s.ubicacion(), s.getLatitud(), s.getLongitud(),
                s.getFecha().toString(), s.getHora(), s.getFechaFiscalizacion(), s.getHoraFiscalizacion(), s.getFiscalizador() == null ? "" : s.getFiscalizador().getId(),
                s.getFiscalizador() == null ? "" : s.getFiscalizador().getNombre(), s.getEstado().toFrontend(), s.getObservaciones(),
                s.getResultadoVisita(), s.getFechaRegistro().toString(), historial,
                s.getResultadoFiscalizacion() == null ? ResultadoFiscalizacion.PENDIENTE.name() : s.getResultadoFiscalizacion().name(),
                s.getObservacionesFiscalizador(), s.getReporteNombreOriginal(), s.getReporteTamano(), s.getFechaEjecucion());
    }
    public FiscalizadorResponse toResponse(Fiscalizador f) { return new FiscalizadorResponse(f.getId(), f.getCodigo(), f.getNombre(), f.getZona(), f.getTelefono(), f.isActivo()); }
}

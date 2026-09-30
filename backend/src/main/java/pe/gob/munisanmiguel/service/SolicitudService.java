package pe.gob.munisanmiguel.service;

import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.gob.munisanmiguel.dto.ApiDtos.*;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.exception.ConflictException;
import pe.gob.munisanmiguel.exception.NotFoundException;
import pe.gob.munisanmiguel.mapper.SolicitudMapper;
import pe.gob.munisanmiguel.repository.*;
import java.time.*;
import java.util.*;

@Service
public class SolicitudService {
    private final SolicitudRepository solicitudes; private final FiscalizadorRepository fiscalizadores; private final AppUserRepository users; private final SolicitudMapper mapper; private final DocumentoStorageService documentos; private final ExpedienteFiscalizacionService expedientes;
    @Autowired public SolicitudService(SolicitudRepository s, FiscalizadorRepository f, AppUserRepository u, SolicitudMapper m, DocumentoStorageService d, ExpedienteFiscalizacionService e) { solicitudes=s; fiscalizadores=f; users=u; mapper=m; documentos=d; expedientes=e; }
    public SolicitudService(SolicitudRepository s, FiscalizadorRepository f, AppUserRepository u, SolicitudMapper m, DocumentoStorageService d) { this(s, f, u, m, d, null); }
    public SolicitudService(SolicitudRepository s, FiscalizadorRepository f, SolicitudMapper m, DocumentoStorageService d) { this(s, f, null, m, d, null); }
    @Transactional public void expirarPendientes() {
        // Compatibility hook for legacy endpoints. Expiration now updates ProgramacionFiscalizacion only.
        if(expedientes!=null)expedientes.vencerProgramaciones();
    }
    void expirarPendientes(LocalDateTime ahora) {
        // Kept package-visible for old callers/tests while the legacy Solicitud model is phased out.
    }
    @Transactional(readOnly=true) public List<SolicitudResponse> buscar(String q,String estado,LocalDate fecha,String fiscalizadorId){String term=q==null?"":q.trim().toLowerCase();EstadoSolicitud parsed=estado==null||estado.isBlank()?null:EstadoSolicitud.fromFrontend(estado);return solicitudes.findAllByOrderByFechaRegistroDescIdDesc().stream().filter(s->fecha==null||s.getFecha().equals(fecha)).filter(s->parsed==null||s.getEstado()==parsed).filter(s->fiscalizadorId==null||fiscalizadorId.isBlank()||(s.getFiscalizador()!=null&&s.getFiscalizador().getId().equals(fiscalizadorId))).filter(s->term.isBlank()||s.getCodigo().toLowerCase().contains(term)||s.nombreCompleto().toLowerCase().contains(term)||s.getDni().contains(term)||s.getDireccion().toLowerCase().contains(term)||(s.getFiscalizador()!=null&&s.getFiscalizador().getNombre().toLowerCase().contains(term))).map(mapper::toResponse).toList();}
    @Transactional(readOnly=true) public SolicitudResponse porCodigo(String codigo){return mapper.toResponse(entidad(codigo));}
    @Transactional(readOnly=true) public Documento documento(String codigo){var s=entidad(codigo);return archivo(s.getDocumentoNombreInterno(),s.getDocumento(),"Documento no encontrado.");}
    @Transactional(readOnly=true) public Documento reporte(String codigo){var s=entidad(codigo);return archivo(s.getReporteNombreInterno(),s.getReporteNombreOriginal(),"Reporte de fiscalización no encontrado.");}
    private Documento archivo(String interno,String original,String error){Resource r=documentos.load(interno);if(r==null)throw new NotFoundException(error);return new Documento(r,original==null?"documento.pdf":original);}
    @Transactional public SolicitudResponse crear(SolicitudRequest r,MultipartFile file,String actor){rechazarExpiradoManual(r.estado(),null);return guardar(null,r,file,actor);}
    @Transactional public SolicitudResponse actualizar(String codigo,SolicitudRequest r,MultipartFile file,String actor){var s=entidad(codigo);rechazarExpiradoManual(r.estado(),s.getEstado());return guardar(s,r,file,actor);}
    private void rechazarExpiradoManual(String pedido,EstadoSolicitud actual){
        if(pedido!=null&&!pedido.isBlank()&&EstadoSolicitud.fromFrontend(pedido)==EstadoSolicitud.EXPIRADO&&actual!=EstadoSolicitud.EXPIRADO)
            throw new ConflictException("EXPIRADO se conserva solo para registros históricos; el vencimiento corresponde a la programación.");
    }
    private SolicitudResponse guardar(Solicitud s,SolicitudRequest r,MultipartFile file,String actor){boolean nuevo=s==null;EstadoSolicitud anterior=nuevo?null:s.getEstado();if(nuevo){s=new Solicitud();s.setCodigo(nuevoCodigo());s.setFechaRegistro(today());}String documentoAnterior=s.getDocumentoNombreInterno();var stored=file==null||file.isEmpty()?null:documentos.store(file);s.setNombres(r.nombres().trim());s.setApellidos(r.apellidos().trim());s.setDni(r.dni());s.setTelefono(r.telefono().trim());s.setDireccion(r.direccion().trim());s.setReferencia(blank(r.referencia()));if(stored!=null){s.setDocumento(stored.originalName());s.setDocumentoTamano(Math.round(stored.size()/1024.0)+" KB");s.setDocumentoNombreInterno(stored.internalName());s.setDocumentoContentType(stored.contentType());s.setDocumentoRuta(stored.relativePath());s.setDocumentoFechaSubida(stored.uploadedAt());}if(nuevo&&s.getDocumentoNombreInterno()==null)throw new ConflictException("Debe adjuntar el documento PDF.");s.setLatitud(r.latitud());s.setLongitud(r.longitud());s.setFecha(r.fecha());s.setHora(r.hora());s.setFechaFiscalizacion(r.fechaFiscalizacion());s.setHoraFiscalizacion(r.horaFiscalizacion());s.setFiscalizador(r.fiscalizadorId()==null||r.fiscalizadorId().isBlank()?null:fiscalizadores.findById(r.fiscalizadorId()).orElseThrow(()->new NotFoundException("Fiscalizador no encontrado.")));validarHorario(s);s.setEstado(r.estado()==null||r.estado().isBlank()?(nuevo?EstadoSolicitud.EN_ESPERA:s.getEstado()):EstadoSolicitud.fromFrontend(r.estado()));s.setObservaciones(r.observaciones());try{solicitudes.save(s);if(nuevo)agregarHistorial(s,EstadoSolicitud.EN_ESPERA,"Registro en ventanilla");else if(anterior!=s.getEstado())agregarHistorial(s,s.getEstado(),"Cambio de estado desde edición de solicitud");if(expedientes!=null)expedientes.sincronizarSolicitud(s,actor);}catch(RuntimeException ex){if(stored!=null)documentos.delete(stored.internalName());throw ex;}if(stored!=null&&documentoAnterior!=null&&!documentoAnterior.equals(stored.internalName()))documentos.delete(documentoAnterior);return mapper.toResponse(s);}
    @Transactional public SolicitudResponse cambiarEstado(String codigo,EstadoRequest r){
        var s=entidad(codigo);var e=EstadoSolicitud.fromFrontend(r.estado());
        if(e==EstadoSolicitud.EXPIRADO)throw new ConflictException("EXPIRADO se conserva solo para registros históricos; el vencimiento corresponde a la programación.");
        s.setEstado(e);
        if(r.resultado()!=null&&!r.resultado().isBlank())s.setResultadoVisita(r.resultado());
        if(r.observacionAdicional()!=null&&!r.observacionAdicional().isBlank())s.setObservaciones((s.getObservaciones()==null?"":s.getObservaciones()+"\n")+r.observacionAdicional());
        agregarHistorial(s,e,r.resultado()!=null?r.resultado():r.observacionAdicional());return mapper.toResponse(s);
    }
    @Transactional public void eliminar(String codigo){var s=entidad(codigo);solicitudes.delete(s);documentos.delete(s.getDocumentoNombreInterno());documentos.delete(s.getReporteNombreInterno());}
    @Transactional(readOnly=true) public List<ProgramacionResponse> programacion(LocalDate fecha){return solicitudes.findAllByFechaFiscalizacionAndHoraFiscalizacionIsNotNullOrderByHoraFiscalizacionAsc(fecha).stream().map(s->new ProgramacionResponse(s.getCodigo(),s.getHoraFiscalizacion(),s.nombreCompleto(),s.getFiscalizador()==null?"Sin asignar":s.getFiscalizador().getNombre(),s.getEstado().toFrontend())).toList();}
    @Transactional(readOnly=true) public List<FiscalizadorTaskResponse> misTareas(Authentication a,LocalDate fecha){var u=usuario(a);if(u.getFiscalizador()==null)throw new NotFoundException("El usuario no tiene fiscalizador asociado.");return solicitudes.findAllByFiscalizadorIdAndFechaFiscalizacionOrderByHoraFiscalizacionAsc(u.getFiscalizador().getId(),fecha==null?today():fecha).stream().map(this::task).toList();}
    @Transactional(readOnly=true) public List<FiscalizadorTaskResponse> misProximas(Authentication a){var u=usuario(a);if(u.getFiscalizador()==null)throw new NotFoundException("El usuario no tiene fiscalizador asociado.");return solicitudes.findAllByFiscalizadorIdAndFechaFiscalizacionGreaterThanOrderByFechaFiscalizacionAscHoraFiscalizacionAsc(u.getFiscalizador().getId(),today()).stream().filter(s->s.getResultadoFiscalizacion()==null||s.getResultadoFiscalizacion()==ResultadoFiscalizacion.PENDIENTE).map(this::task).toList();}
    @Transactional(readOnly=true) public List<FiscalizadorTaskResponse> miHistorial(Authentication a,LocalDate fecha,ResultadoFiscalizacion resultado){var u=usuario(a);if(u.getFiscalizador()==null)throw new NotFoundException("El usuario no tiene fiscalizador asociado.");var resultados=resultado==null?List.of(ResultadoFiscalizacion.REALIZADA,ResultadoFiscalizacion.NO_REALIZADA):List.of(resultado);return solicitudes.findAllByFiscalizadorIdAndResultadoFiscalizacionInOrderByFechaFiscalizacionDescHoraFiscalizacionDesc(u.getFiscalizador().getId(),resultados).stream().filter(s->fecha==null||fecha.equals(s.getFechaFiscalizacion())).map(this::task).toList();}
    @Transactional(readOnly=true) public Solicitud tareaPropia(String codigo,Authentication a){var s=entidad(codigo);validarPropiedad(s,a);return s;}
    @Transactional(readOnly=true) public SolicitudResponse mapperResponse(Solicitud s){return mapper.toResponse(s);}
    @Transactional public SolicitudResponse registrarResultado(String codigo,ResultadoFiscalizacionRequest r,MultipartFile reporte,Authentication a){
        var s=tareaPropia(codigo,a);
        if(expedientes!=null)expedientes.validarResultadoRegistrable(s);
        if(expedientes==null&&s.getEstado()==EstadoSolicitud.EXPIRADO)throw new ConflictException("La fiscalización expiró sin resultado; debe revisarse su programación.");
        if(r.resultado()==ResultadoFiscalizacion.PENDIENTE)throw new ConflictException("Debe registrar un resultado definitivo.");
        if(r.resultado()==ResultadoFiscalizacion.REALIZADA&&(reporte==null||reporte.isEmpty()))throw new ConflictException("Debe adjuntar el reporte PDF para una fiscalización realizada.");
        if(r.resultado()==ResultadoFiscalizacion.NO_REALIZADA&&(r.observaciones()==null||r.observaciones().isBlank()))throw new ConflictException("Debe registrar el motivo de la fiscalización no realizada.");
        String anterior=s.getReporteNombreInterno();var stored=reporte==null||reporte.isEmpty()?null:documentos.store(reporte);
        s.setResultadoFiscalizacion(r.resultado());s.setObservacionesFiscalizador(r.observaciones()==null?"":r.observaciones().trim());s.setFechaEjecucion(LocalDateTime.now(ZoneId.of("America/Lima")));
        if(stored!=null){s.setReporteNombreOriginal(stored.originalName());s.setReporteNombreInterno(stored.internalName());s.setReporteContentType(stored.contentType());s.setReporteTamano(stored.size());s.setReporteRuta(stored.relativePath());s.setReporteFechaSubida(stored.uploadedAt());}
        String nota="Resultado operativo: "+r.resultado().name()+(r.resultado()==ResultadoFiscalizacion.REALIZADA?"; reporte PDF adjunto":"; motivo: "+s.getObservacionesFiscalizador());
        agregarHistorial(s,s.getEstado(),nota.length()>500?nota.substring(0,500):nota);
        if(expedientes!=null)expedientes.sincronizarResultado(s,a.getName());
        if(stored!=null&&anterior!=null&&!anterior.equals(stored.internalName()))documentos.delete(anterior);
        return mapper.toResponse(s);
    }
    private AppUser usuario(Authentication a){return users.findByUsernameIgnoreCaseAndActivoTrue(a.getName()).orElseThrow(()->new NotFoundException("Usuario fiscalizador no encontrado."));}
    private void validarPropiedad(Solicitud s,Authentication a){var u=usuario(a);if(u.getFiscalizador()==null||s.getFiscalizador()==null||!u.getFiscalizador().getId().equals(s.getFiscalizador().getId()))throw new NotFoundException("Fiscalización no encontrada.");}
    private FiscalizadorTaskResponse task(Solicitud s){return new FiscalizadorTaskResponse(s.getCodigo(),s.nombreCompleto(),s.getDni(),s.getTelefono(),s.getDireccion(),s.getReferencia(),s.getFechaFiscalizacion(),s.getHoraFiscalizacion(),s.getLatitud(),s.getLongitud(),s.getDocumento(),s.getResultadoFiscalizacion()==null?ResultadoFiscalizacion.PENDIENTE.name():s.getResultadoFiscalizacion().name(),s.getObservacionesFiscalizador(),s.getReporteNombreOriginal(),s.getReporteTamano(),s.getFechaEjecucion(),s.getEstado().toFrontend(),s.getObservaciones());}
    private Solicitud entidad(String codigo){return solicitudes.findByCodigo(codigo).orElseThrow(()->new NotFoundException("Solicitud no encontrada: "+codigo));}
    private void agregarHistorial(Solicitud s,EstadoSolicitud e,String nota){var h=new SolicitudHistorial();h.setSolicitud(s);h.setFecha(today());h.setEstado(e);h.setNota(nota);s.getHistorialCambios().add(h);solicitudes.save(s);}
    private void validarHorario(Solicitud s){if(s.getFechaFiscalizacion()==null||s.getHoraFiscalizacion()==null||s.getFiscalizador()==null)return;String f=s.getFiscalizador().getId();boolean ocupado=s.getId()==null?solicitudes.existsByFechaFiscalizacionAndHoraFiscalizacionAndFiscalizadorId(s.getFechaFiscalizacion(),s.getHoraFiscalizacion(),f):solicitudes.existsByFechaFiscalizacionAndHoraFiscalizacionAndFiscalizadorIdAndIdNot(s.getFechaFiscalizacion(),s.getHoraFiscalizacion(),f,s.getId());if(ocupado)throw new ConflictException("El fiscalizador ya tiene una fiscalización programada para esa fecha y hora.");}
    private LocalDate today(){return LocalDate.now(ZoneId.of("America/Lima"));}
    private String nuevoCodigo(){int year=today().getYear();int max=solicitudes.findAll().stream().map(Solicitud::getCodigo).filter(x->x.matches("SM-"+year+"-\\d+")).mapToInt(x->Integer.parseInt(x.substring(x.lastIndexOf('-')+1))).max().orElse(425);return "SM-"+year+"-"+String.format("%05d",max+1);}
    private String blank(String value){return value==null?"":value;}
    public record Documento(Resource recurso,String nombre){}
}

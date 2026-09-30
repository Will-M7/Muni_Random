package pe.gob.munisanmiguel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.gob.munisanmiguel.dto.ApiDtos.*;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.exception.ConflictException;
import pe.gob.munisanmiguel.exception.NotFoundException;
import pe.gob.munisanmiguel.repository.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class DiligenciaFiscalizacionService {
    private static final ZoneId LIMA=ZoneId.of("America/Lima");
    private final ProgramacionFiscalizacionRepository programaciones;
    private final DiligenciaFiscalizacionRepository diligencias;
    private final DiligenciaParticipanteRepository participantes;
    private final EvidenciaFiscalizacionRepository evidencias;
    private final ActaFiscalizacionRepository actas;
    private final AppUserRepository usuarios;
    private final ExpedienteHistorialRepository historial;
    private final FiscalizacionFileStorageService storage;
    private final DocumentoStorageService legacyStorage;
    private final ActaPdfService pdf;
    private final ObjectMapper json;
    private final RevisionFiscalizacionRepository revisiones;

    public DiligenciaFiscalizacionService(ProgramacionFiscalizacionRepository programaciones,
            DiligenciaFiscalizacionRepository diligencias,DiligenciaParticipanteRepository participantes,
            EvidenciaFiscalizacionRepository evidencias,ActaFiscalizacionRepository actas,AppUserRepository usuarios,
            ExpedienteHistorialRepository historial,FiscalizacionFileStorageService storage,
            DocumentoStorageService legacyStorage,ActaPdfService pdf,ObjectMapper json,RevisionFiscalizacionRepository revisiones){
        this.programaciones=programaciones;this.diligencias=diligencias;this.participantes=participantes;
        this.evidencias=evidencias;this.actas=actas;this.usuarios=usuarios;this.historial=historial;
        this.storage=storage;this.legacyStorage=legacyStorage;this.pdf=pdf;this.json=json;this.revisiones=revisiones;
    }

    @Transactional
    public DiligenciaWorkspaceResponse espacio(String username,Long programacionId){
        var p=programaciones.findById(programacionId).orElseThrow(()->new NotFoundException("Programación no encontrada."));
        var user=usuario(username);validarPropiedad(p,user);
        var d=diligencias.findByProgramacionId(programacionId).orElse(null);
        return workspace(p,d);
    }

    @Transactional
    public DiligenciaWorkspaceResponse iniciar(Long programacionId,InicioDiligenciaRequest r,String username){
        var p=programaciones.findByIdForUpdate(programacionId).orElseThrow(()->new NotFoundException("Programación no encontrada."));
        var user=usuario(username);validarPropiedad(p,user);
        if(p.getEstadoProgramacion()==EstadoProgramacion.PROGRAMADA&&LocalDateTime.of(p.getFecha(),p.getHora()).isBefore(now())){
            p.setEstadoProgramacion(EstadoProgramacion.VENCIDA);
            evento(p.getExpediente(),p,"PROGRAMACION_VENCIDA","La programación venció antes de iniciar la diligencia.",map("estado","PROGRAMADA"),map("estado","VENCIDA"),username);
            return workspace(p,null);
        }
        if(p.getEstadoProgramacion()!=EstadoProgramacion.PROGRAMADA)
            throw new ConflictException("Solo se puede iniciar una programación vigente en estado PROGRAMADA.");
        if(diligencias.findByProgramacionId(programacionId).isPresent())
            throw new ConflictException("Esta programación ya tiene una diligencia iniciada.");
        var place=gps(r==null?null:r.estadoGps(),r==null?null:r.latitud(),r==null?null:r.longitud());
        LocalDateTime now=now();var d=new DiligenciaFiscalizacion();d.setExpediente(p.getExpediente());d.setProgramacion(p);
        d.setFiscalizadorResponsable(p.getFiscalizador());d.setFechaInicio(now);d.setLatitudInicio(place.lat);d.setLongitudInicio(place.lon);
        d.setGpsInicioEstado(place.status);d.setGpsCierreEstado(EstadoGps.NO_DISPONIBLE);d.setLugarDiligencia(p.getExpediente().getDireccionFiscalizada());
        d.setEstado(EstadoDiligencia.INICIADA);d.setSituacionPersona(SituacionPersona.PENDIENTE);d.setMedioEntrega(MedioEntrega.PENDIENTE);
        d.setCreatedAt(now);d.setUpdatedAt(now);d=diligencias.saveAndFlush(d);
        p.setEstadoProgramacion(EstadoProgramacion.EN_CURSO);
        if(p.getExpediente().getEstado()==EstadoExpediente.ABIERTO)p.getExpediente().setEstado(EstadoExpediente.EN_FISCALIZACION);
        evento(d,"DILIGENCIA_INICIADA","Diligencia iniciada; GPS: "+place.status.name(),null,map("estado","INICIADA","fechaInicio",now,"gps",place.status.name()),username);
        var responsible=new DiligenciaParticipante();responsible.setDiligencia(d);responsible.setTipoParticipante(TipoParticipante.FISCALIZADOR_RESPONSABLE);
        responsible.setNombre(p.getFiscalizador().getNombre());responsible.setCargoCondicion(user.getCargo());responsible.setCreadoPor(user);responsible.setCreadoEn(now);
        participantes.save(responsible);
        evento(d,"PARTICIPANTE_AGREGADO","Fiscalizador responsable incorporado automáticamente",null,map("tipo","FISCALIZADOR_RESPONSABLE","nombre",p.getFiscalizador().getNombre()),username);
        return workspace(p,d);
    }

    @Transactional
    public DiligenciaWorkspaceResponse guardar(Long id,GuardarDiligenciaRequest r,String username){
        var d=diligenciaBloqueada(id);validarPropiedad(d.getProgramacion(),usuario(username));editable(d);
        d.setEstado(EstadoDiligencia.EN_DESARROLLO);d.setUpdatedAt(now());
        if(r.situacionPersona()!=null)d.setSituacionPersona(r.situacionPersona());
        d.setPersonaNombres(blank(r.personaNombres()));d.setPersonaApellidos(blank(r.personaApellidos()));
        d.setPersonaTipoDocumento(blank(r.personaTipoDocumento()));d.setPersonaNumeroDocumento(blank(r.personaNumeroDocumento()));
        d.setPersonaCondicion(blank(r.personaCondicion()));d.setPersonaObservaciones(blank(r.personaObservaciones()));
        d.setHechosConstatados(blank(r.hechosConstatados()));d.setOcurrencias(blank(r.ocurrencias()));
        d.setObservacionesFiscalizador(blank(r.observacionesFiscalizador()));d.setObservacionesFiscalizado(blank(r.observacionesFiscalizado()));
        return workspace(d.getProgramacion(),d);
    }

    @Transactional
    public ParticipanteResponse agregarParticipante(Long id,ParticipanteRequest r,String username){
        var d=diligenciaBloqueada(id);var user=usuario(username);validarPropiedad(d.getProgramacion(),user);editable(d);
        if(r.tipoParticipante()==TipoParticipante.FISCALIZADOR_RESPONSABLE)
            throw new ConflictException("El fiscalizador responsable ya se incorpora automáticamente.");
        var p=new DiligenciaParticipante();p.setDiligencia(d);p.setTipoParticipante(r.tipoParticipante());p.setNombre(r.nombre().trim());
        p.setApellidos(blank(r.apellidos()));p.setTipoDocumento(blank(r.tipoDocumento()));p.setNumeroDocumento(blank(r.numeroDocumento()));
        p.setEntidadArea(blank(r.entidadArea()));p.setCargoCondicion(blank(r.cargoCondicion()));p.setObservaciones(blank(r.observaciones()));
        p.setCreadoPor(user);p.setCreadoEn(now());p=participantes.save(p);d.setUpdatedAt(now());
        evento(d,"PARTICIPANTE_AGREGADO","Participante agregado: "+r.tipoParticipante().name(),null,map("tipo",r.tipoParticipante().name(),"nombre",p.getNombre(),"apellidos",p.getApellidos()),username);
        return participante(p);
    }

    @Transactional
    public EvidenciaResponse agregarEvidencia(Long id,TipoEvidencia tipo,String descripcion,MultipartFile file,String username){
        var d=diligenciaBloqueada(id);var user=usuario(username);validarPropiedad(d.getProgramacion(),user);editable(d);
        if(tipo==null)throw new ConflictException("Indica el tipo de evidencia.");
        var stored=storage.storeEvidence(file,tipo==TipoEvidencia.FOTO);
        try{
            var e=new EvidenciaFiscalizacion();e.setDiligencia(d);e.setTipo(tipo);e.setNombreOriginal(stored.originalName());e.setNombreInterno(stored.internalName());
            e.setTipoMime(stored.mimeType());e.setTamanoBytes(stored.size());e.setDescripcion(blank(descripcion));e.setCreadoPor(user);e.setFechaHora(now());
            e=evidencias.save(e);d.setUpdatedAt(now());evento(d,"EVIDENCIA_AGREGADA","Evidencia agregada: "+stored.originalName(),null,map("evidenciaId",e.getId(),"tipo",tipo.name(),"nombre",stored.originalName()),username);
            return evidencia(e);
        }catch(RuntimeException ex){storage.delete(stored.internalName());throw ex;}
    }

    @Transactional
    public DiligenciaWorkspaceResponse noRealizada(Long programacionId,NoRealizadaRequest r,String username){
        var p=programaciones.findByIdForUpdate(programacionId).orElseThrow(()->new NotFoundException("Programación no encontrada."));
        var user=usuario(username);validarPropiedad(p,user);
        if(r.motivo()==MotivoNoRealizada.OTRO&&(blank(r.detalleMotivo())==null))throw new ConflictException("Describe el motivo cuando seleccionas OTRO.");
        DiligenciaFiscalizacion d;
        if(p.getEstadoProgramacion()==EstadoProgramacion.PROGRAMADA){
            var point=gps(r.estadoGps(),r.latitud(),r.longitud());LocalDateTime now=now();d=new DiligenciaFiscalizacion();
            d.setExpediente(p.getExpediente());d.setProgramacion(p);d.setFiscalizadorResponsable(p.getFiscalizador());d.setFechaInicio(now);
            d.setLatitudInicio(point.lat);d.setLongitudInicio(point.lon);d.setGpsInicioEstado(point.status);d.setGpsCierreEstado(EstadoGps.NO_DISPONIBLE);
            d.setLugarDiligencia(p.getExpediente().getDireccionFiscalizada());d.setSituacionPersona(SituacionPersona.PENDIENTE);
            d.setEstado(EstadoDiligencia.NO_REALIZADA);d.setMedioEntrega(MedioEntrega.PENDIENTE);d.setCreatedAt(now);d.setUpdatedAt(now);d.setFechaCierre(now);
            d.setMotivoNoRealizada(r.motivo());d.setDetalleMotivoNoRealizada(blank(r.detalleMotivo()));d.setOcurrencias(blank(r.ocurrencias()));
            d.setObservacionesFiscalizador(blank(r.observacionesFiscalizador()));d=diligencias.saveAndFlush(d);
            var responsible=new DiligenciaParticipante();responsible.setDiligencia(d);responsible.setTipoParticipante(TipoParticipante.FISCALIZADOR_RESPONSABLE);
            responsible.setNombre(p.getFiscalizador().getNombre());responsible.setCargoCondicion(user.getCargo());responsible.setCreadoPor(user);responsible.setCreadoEn(now);participantes.save(responsible);
        }else if(p.getEstadoProgramacion()==EstadoProgramacion.EN_CURSO){
            d=diligencias.findByProgramacionId(p.getId()).orElseThrow(()->new ConflictException("No se encontró la diligencia iniciada."));
            d=diligenciaBloqueada(d.getId());editable(d);d.setEstado(EstadoDiligencia.NO_REALIZADA);d.setFechaCierre(now());d.setUpdatedAt(now());
            var point=gps(r.estadoGps(),r.latitud(),r.longitud());d.setLatitudCierre(point.lat);d.setLongitudCierre(point.lon);d.setGpsCierreEstado(point.status);
            d.setMotivoNoRealizada(r.motivo());d.setDetalleMotivoNoRealizada(blank(r.detalleMotivo()));d.setOcurrencias(blank(r.ocurrencias()));d.setObservacionesFiscalizador(blank(r.observacionesFiscalizador()));
        }else throw new ConflictException("Solo se puede registrar una visita programada o en curso como NO REALIZADA.");
        p.setEstadoProgramacion(EstadoProgramacion.NO_REALIZADA);
        p.getExpediente().setEstado(EstadoExpediente.ABIERTO);
        String motivoDetalle=blank(r.detalleMotivo());
        evento(d,"DILIGENCIA_NO_REALIZADA","Visita no realizada: "+r.motivo().name()+(motivoDetalle==null?"":". "+motivoDetalle),null,map("estado","NO_REALIZADA","motivo",r.motivo().name()),username);
        return workspace(p,d);
    }

    @Transactional
    public ActaResponse finalizar(Long id,FinalizarDiligenciaRequest r,String username){
        var d=diligenciaBloqueada(id);var user=usuario(username);validarPropiedad(d.getProgramacion(),user);editable(d);
        if(d.getFechaInicio()==null)throw new ConflictException("La diligencia debe tener hora de inicio.");
        if(d.getProgramacion().getEstadoProgramacion()!=EstadoProgramacion.EN_CURSO)throw new ConflictException("La programación ya no está en curso.");
        if(blank(d.getHechosConstatados())==null)throw new ConflictException("Registra los hechos constatados antes de finalizar.");
        if(d.getSituacionPersona()==null||d.getSituacionPersona()==SituacionPersona.PENDIENTE)
            throw new ConflictException("Indica quién atendió o deja constancia de su ausencia o negativa a identificarse.");
        if(d.getSituacionPersona()==SituacionPersona.IDENTIFICADA&&
                (blank(d.getPersonaNombres())==null||blank(d.getPersonaApellidos())==null||blank(d.getPersonaTipoDocumento())==null||blank(d.getPersonaNumeroDocumento())==null||blank(d.getPersonaCondicion())==null))
            throw new ConflictException("Completa la identificación y condición de la persona presente.");
        if(d.getSituacionPersona()==SituacionPersona.SE_NEGO_A_IDENTIFICARSE&&blank(d.getPersonaObservaciones())==null)
            throw new ConflictException("Describe la negativa a identificarse.");
        if((r.firmaFiscalizado()==EstadoFirmaActa.SE_NEGO_A_FIRMAR||r.firmaFiscalizado()==EstadoFirmaActa.SE_NEGO_A_IDENTIFICARSE)&&blank(r.observacionFirma())==null)
            throw new ConflictException("Registra la constancia de la negativa.");
        if(r.firmaFiscalizado()==EstadoFirmaActa.SE_NEGO_A_IDENTIFICARSE&&d.getSituacionPersona()!=SituacionPersona.SE_NEGO_A_IDENTIFICARSE)
            throw new ConflictException("La negativa a identificarse debe quedar consignada como situación de la persona presente.");
        if(r.copiaEntregada()&&r.medioEntrega()==MedioEntrega.PENDIENTE)throw new ConflictException("Indica cómo se entregó la copia del acta.");
        var close=gps(r.estadoGpsCierre(),r.latitudCierre(),r.longitudCierre());LocalDateTime now=now();
        d.setFechaCierre(now);d.setLatitudCierre(close.lat);d.setLongitudCierre(close.lon);d.setGpsCierreEstado(close.status);
        d.setEstado(EstadoDiligencia.FINALIZADA);d.setCopiaEntregada(r.copiaEntregada());d.setMedioEntrega(r.medioEntrega());d.setUpdatedAt(now);
        var p=d.getProgramacion();p.setEstadoProgramacion(EstadoProgramacion.REALIZADA);d.getExpediente().setEstado(EstadoExpediente.PENDIENTE_REVISION);
        var acta=new ActaFiscalizacion();acta.setCodigo("TMP"+UUID.randomUUID().toString().substring(0,20));acta.setDiligencia(d);
        acta.setFirmaFiscalizadoEstado(r.firmaFiscalizado());acta.setFirmaFiscalizadoObservacion(blank(r.observacionFirma()));acta.setGeneradoPor(user);acta.setGeneradoEn(now);
        acta.setPdfGeneradoNombre("Acta-"+d.getExpediente().getCodigo()+".pdf");acta.setPdfGeneradoInterno("pending-"+UUID.randomUUID()+".pdf");acta.setContenidoJson("{}");
        actas.saveAndFlush(acta);acta.setCodigo("ACT-"+now.getYear()+"-"+String.format("%05d",acta.getId()));
        Map<String,Object> snapshot=snapshot(d,acta.getCodigo(),r.firmaFiscalizado().name(),blank(r.observacionFirma()));
        try{acta.setContenidoJson(json.writeValueAsString(snapshot));}catch(Exception ex){throw new IllegalStateException("No se pudo serializar el acta.",ex);}
        var stored=storage.storeGeneratedPdf(pdf.generar(snapshot),acta.getPdfGeneradoNombre());acta.setPdfGeneradoInterno(stored.internalName());
        var revision=new RevisionFiscalizacion();revision.setExpediente(d.getExpediente());revision.setDiligencia(d);revision.setActa(acta);revision.setEstadoRevision(EstadoRevision.PENDIENTE);revision.setRequiereSeguimiento(false);revision.setRequiereDerivacion(false);revision.setCreadoEn(now);revision.setActualizadoEn(now);revisiones.save(revision);
        evento(d,"DILIGENCIA_FINALIZADA","Diligencia finalizada y programación marcada REALIZADA",map("estado","EN_DESARROLLO"),map("estado","FINALIZADA"),username);
        evento(d,"ACTA_GENERADA","Acta de Fiscalización generada: "+acta.getCodigo(),null,map("actaId",acta.getId(),"codigo",acta.getCodigo()),username);
        if(r.copiaEntregada())evento(d,"COPIA_ENTREGADA","Se dejó constancia de entrega de copia por medio "+r.medioEntrega().name(),null,map("medio",r.medioEntrega().name()),username);
        return actaResponse(acta,snapshot);
    }

    @Transactional(readOnly=true)
    public List<ActaResponse> actasExpediente(String codigo,String username,boolean ownOnly){
        var list=actas.findAllByDiligenciaExpedienteCodigoOrderByGeneradoEnAsc(codigo);
        return list.stream().filter(a->!ownOnly||isOwner(a.getDiligencia().getProgramacion(),username)).map(this::actaResponse).toList();
    }

    @Transactional(readOnly=true)
    public ActaResponse acta(Long id,String username,boolean ownOnly){var a=actas.findById(id).orElseThrow(()->new NotFoundException("Acta no encontrada."));if(ownOnly)requireOwner(a.getDiligencia().getProgramacion(),username);return actaResponse(a);}

    @Transactional(readOnly=true)
    public FileDownload pdfActa(Long id,String username,boolean signed,boolean ownOnly){
        var a=actas.findById(id).orElseThrow(()->new NotFoundException("Acta no encontrada."));if(ownOnly)requireOwner(a.getDiligencia().getProgramacion(),username);
        String file=signed?a.getPdfFirmadoInterno():a.getPdfGeneradoInterno();String name=signed?a.getPdfFirmadoNombre():a.getPdfGeneradoNombre();
        if(file==null)throw new NotFoundException("No existe una copia firmada para esta acta.");Resource resource=storage.load(file);
        if(resource==null)throw new NotFoundException("El archivo del acta no está disponible.");return new FileDownload(resource,name,"application/pdf");
    }

    @Transactional
    public ActaResponse subirCopiaFirmada(Long id,MultipartFile file,String username){
        var a=actas.findById(id).orElseThrow(()->new NotFoundException("Acta no encontrada."));var d=a.getDiligencia();var user=usuario(username);requireOwner(d.getProgramacion(),user);
        if(a.getPdfFirmadoInterno()!=null)throw new ConflictException("Ya existe una copia firmada. No se puede sobrescribir.");
        var stored=storage.storePdf(file);a.setPdfFirmadoNombre(stored.originalName());a.setPdfFirmadoInterno(stored.internalName());
        evento(d,"ACTA_FIRMADA_SUBIDA","Se adjuntó copia firmada en papel del acta",null,map("actaId",id,"nombre",stored.originalName()),username);
        return actaResponse(a);
    }

    @Transactional(readOnly=true)
    public FileDownload sustento(Long programacionId,String username){var p=programaciones.findById(programacionId).orElseThrow(()->new NotFoundException("Programación no encontrada."));requireOwner(p,usuario(username));
        var e=p.getExpediente();Resource file=e.getSolicitud()!=null&&e.getDocumentoSustentoInterno()==null?legacyStorage.load(e.getSolicitud().getDocumentoNombreInterno()):legacyStorage.load(e.getDocumentoSustentoInterno());
        if(file==null)throw new NotFoundException("No existe documento de sustento.");return new FileDownload(file,e.getDocumentoSustentoNombre()==null?"sustento.pdf":e.getDocumentoSustentoNombre(),"application/pdf");}

    @Transactional(readOnly=true)
    public FileDownload evidencia(Long id,Long evidenciaId,String username,boolean ownOnly){var e=evidencias.findByIdAndDiligenciaId(evidenciaId,id).orElseThrow(()->new NotFoundException("Evidencia no encontrada."));if(ownOnly)requireOwner(e.getDiligencia().getProgramacion(),username);Resource file=storage.load(e.getNombreInterno());if(file==null)throw new NotFoundException("Archivo de evidencia no encontrado.");return new FileDownload(file,e.getNombreOriginal(),e.getTipoMime());}

    @Transactional(readOnly=true)
    public FileDownload evidenciaActa(Long actaId,Long evidenciaId,String username,boolean ownOnly){var a=actas.findById(actaId).orElseThrow(()->new NotFoundException("Acta no encontrada."));return evidencia(a.getDiligencia().getId(),evidenciaId,username,ownOnly);}

    private DiligenciaFiscalizacion diligenciaBloqueada(Long id){return diligencias.findByIdForUpdate(id).orElseThrow(()->new NotFoundException("Diligencia no encontrada."));}
    private DiligenciaWorkspaceResponse workspace(ProgramacionFiscalizacion p,DiligenciaFiscalizacion d){
        var e=p.getExpediente();var ps=d==null?List.<ParticipanteResponse>of():participantes.findAllByDiligenciaIdOrderByCreadoEnAscIdAsc(d.getId()).stream().map(this::participante).toList();
        var es=d==null?List.<EvidenciaResponse>of():evidencias.findAllByDiligenciaIdOrderByFechaHoraAscIdAsc(d.getId()).stream().map(this::evidencia).toList();
        ActaResumenResponse brief=d==null||d.getActa()==null?null:new ActaResumenResponse(d.getActa().getId(),d.getActa().getCodigo(),d.getActa().getFirmaFiscalizadoEstado().name(),d.getActa().getPdfGeneradoNombre(),d.getActa().getPdfFirmadoNombre(),d.getActa().getGeneradoEn());
        return new DiligenciaWorkspaceResponse(d==null?null:d.getId(),e.getCodigo(),e.getOrigen().name(),e.getObjetoFiscalizacion(),e.getObservacionesIniciales(),
                e.getNombreAdministrado()==null?"":e.getNombreAdministrado()+(e.getAdministradoApellidos()==null?"":" "+e.getAdministradoApellidos()),e.getDocumentoAdministrado(),e.getTelefonoContacto(),e.getDireccionFiscalizada(),e.getReferenciaDomicilio(),e.getLatitud(),e.getLongitud(),
                p.getId(),p.getFecha(),p.getHora(),p.getTipoVisita().name(),p.getEstadoProgramacion().name(),e.getEstado().name(),d==null?null:d.getEstado().name(),
                d==null?null:d.getFechaInicio(),d==null?null:d.getFechaCierre(),d==null?null:d.getLatitudInicio(),d==null?null:d.getLongitudInicio(),d==null?null:d.getGpsInicioEstado().name(),
                d==null?null:d.getLatitudCierre(),d==null?null:d.getLongitudCierre(),d==null?null:d.getGpsCierreEstado().name(),e.getDireccionFiscalizada(),
                d==null?null:d.getSituacionPersona().name(),d==null?null:d.getPersonaNombres(),d==null?null:d.getPersonaApellidos(),d==null?null:d.getPersonaTipoDocumento(),d==null?null:d.getPersonaNumeroDocumento(),d==null?null:d.getPersonaCondicion(),d==null?null:d.getPersonaObservaciones(),
                d==null?null:d.getHechosConstatados(),d==null?null:d.getOcurrencias(),d==null?null:d.getObservacionesFiscalizador(),d==null?null:d.getObservacionesFiscalizado(),d==null||d.getMotivoNoRealizada()==null?null:d.getMotivoNoRealizada().name(),d==null?null:d.getDetalleMotivoNoRealizada(),d!=null&&d.isCopiaEntregada(),d==null?null:d.getMedioEntrega().name(),
                e.getDocumentoSustentoNombre(),ps,es,brief);
    }
    private ParticipanteResponse participante(DiligenciaParticipante p){return new ParticipanteResponse(p.getId(),p.getTipoParticipante().name(),p.getNombre(),p.getApellidos(),p.getTipoDocumento(),p.getNumeroDocumento(),p.getEntidadArea(),p.getCargoCondicion(),p.getObservaciones(),p.getCreadoEn(),p.getCreadoPor()==null?null:p.getCreadoPor().getUsername());}
    private EvidenciaResponse evidencia(EvidenciaFiscalizacion e){return new EvidenciaResponse(e.getId(),e.getTipo().name(),e.getNombreOriginal(),e.getTipoMime(),e.getTamanoBytes(),e.getDescripcion(),e.getFechaHora(),e.getCreadoPor()==null?null:e.getCreadoPor().getUsername());}
    private ActaResponse actaResponse(ActaFiscalizacion a){try{return actaResponse(a,json.readValue(a.getContenidoJson(),new TypeReference<Map<String,Object>>(){}));}catch(Exception e){throw new IllegalStateException("No se pudo leer el contenido persistido del acta.",e);}}
    private ActaResponse actaResponse(ActaFiscalizacion a,Map<String,Object> content){return new ActaResponse(a.getId(),a.getCodigo(),a.getDiligencia().getId(),a.getDiligencia().getExpediente().getCodigo(),a.getFirmaFiscalizadoEstado().name(),a.getFirmaFiscalizadoObservacion(),a.getPdfGeneradoNombre(),a.getPdfFirmadoNombre(),a.getGeneradoEn(),content);}
    private Map<String,Object> snapshot(DiligenciaFiscalizacion d,String code,String signature,String signatureNote){var e=d.getExpediente();var map=new LinkedHashMap<String,Object>();map.put("actaCodigo",code);map.put("expedienteCodigo",e.getCodigo());map.put("origen",e.getOrigen().name());map.put("administrado",e.getNombreAdministrado()==null?"":e.getNombreAdministrado()+" "+(e.getAdministradoApellidos()==null?"":e.getAdministradoApellidos()));map.put("documentoAdministrado",e.getDocumentoAdministrado());map.put("lugar",d.getLugarDiligencia());map.put("fecha",d.getFechaInicio().toLocalDate().toString());map.put("fechaInicio",d.getFechaInicio().toString());map.put("fechaCierre",d.getFechaCierre().toString());map.put("tipoVisita",d.getProgramacion().getTipoVisita().name());map.put("objetoFiscalizacion",e.getObjetoFiscalizacion());map.put("observacionesPrevias",e.getObservacionesIniciales());map.put("fiscalizadorResponsable",d.getFiscalizadorResponsable().getNombre());map.put("situacionPersona",d.getSituacionPersona().name());map.put("personaNombres",d.getPersonaNombres());map.put("personaApellidos",d.getPersonaApellidos());map.put("personaTipoDocumento",d.getPersonaTipoDocumento());map.put("personaNumeroDocumento",d.getPersonaNumeroDocumento());map.put("personaCondicion",d.getPersonaCondicion());map.put("personaObservaciones",d.getPersonaObservaciones());map.put("hechosConstatados",d.getHechosConstatados());map.put("ocurrencias",d.getOcurrencias());map.put("observacionesFiscalizador",d.getObservacionesFiscalizador());map.put("observacionesFiscalizado",d.getObservacionesFiscalizado());map.put("firmaFiscalizadoEstado",signature);map.put("firmaFiscalizadoObservacion",signatureNote);map.put("copiaEntregada",d.isCopiaEntregada());map.put("medioEntrega",d.getMedioEntrega().name());map.put("gpsInicioEstado",d.getGpsInicioEstado().name());map.put("latitudInicio",d.getLatitudInicio());map.put("longitudInicio",d.getLongitudInicio());map.put("gpsCierreEstado",d.getGpsCierreEstado().name());map.put("latitudCierre",d.getLatitudCierre());map.put("longitudCierre",d.getLongitudCierre());
        map.put("participantes",participantes.findAllByDiligenciaIdOrderByCreadoEnAscIdAsc(d.getId()).stream().map(p->{var x=new LinkedHashMap<String,Object>();x.put("tipoParticipante",p.getTipoParticipante().name());x.put("nombre",p.getNombre());x.put("apellidos",p.getApellidos());x.put("tipoDocumento",p.getTipoDocumento());x.put("numeroDocumento",p.getNumeroDocumento());x.put("entidadArea",p.getEntidadArea());x.put("cargoCondicion",p.getCargoCondicion());return x;}).toList());
        map.put("evidencias",evidencias.findAllByDiligenciaIdOrderByFechaHoraAscIdAsc(d.getId()).stream().map(v->{var x=new LinkedHashMap<String,Object>();x.put("tipo",v.getTipo().name());x.put("nombreOriginal",v.getNombreOriginal());x.put("tipoMime",v.getTipoMime());x.put("descripcion",v.getDescripcion());return x;}).toList());return map;}
    private void evento(DiligenciaFiscalizacion d,String type,String description,Map<String,?> before,Map<String,?> after,String username){var h=new ExpedienteHistorial();h.setExpediente(d.getExpediente());h.setProgramacion(d.getProgramacion());h.setFechaHora(now());h.setActorUsername(username);h.setActor(usuarios.findByUsernameIgnoreCaseAndActivoTrue(username).orElse(null));h.setTipoEvento(type);h.setDescripcion(description);h.setValoresAnteriores(serialize(before));h.setValoresNuevos(serialize(after));historial.save(h);}
    private void evento(ExpedienteFiscalizacion expediente,ProgramacionFiscalizacion programacion,String type,String description,Map<String,?> before,Map<String,?> after,String username){var h=new ExpedienteHistorial();h.setExpediente(expediente);h.setProgramacion(programacion);h.setFechaHora(now());h.setActorUsername(username);h.setActor(usuarios.findByUsernameIgnoreCaseAndActivoTrue(username).orElse(null));h.setTipoEvento(type);h.setDescripcion(description);h.setValoresAnteriores(serialize(before));h.setValoresNuevos(serialize(after));historial.save(h);}
    private String serialize(Object value){if(value==null)return null;try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private Map<String,Object> map(Object...args){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<args.length;i+=2)m.put((String)args[i],args[i+1]);return m;}
    private void editable(DiligenciaFiscalizacion d){if(d.getEstado()!=EstadoDiligencia.INICIADA&&d.getEstado()!=EstadoDiligencia.EN_DESARROLLO)throw new ConflictException("La diligencia ya finalizó y no admite cambios ordinarios.");}
    private AppUser usuario(String name){return usuarios.findByUsernameIgnoreCaseAndActivoTrue(name).orElseThrow(()->new NotFoundException("Usuario autenticado no encontrado."));}
    private void validarPropiedad(ProgramacionFiscalizacion p,AppUser user){if(user.getFiscalizador()==null||p.getFiscalizador()==null||!p.getFiscalizador().getId().equals(user.getFiscalizador().getId()))throw new AccessDeniedException("La programación no pertenece al fiscalizador autenticado.");}
    private boolean isOwner(ProgramacionFiscalizacion p,String name){var u=usuarios.findByUsernameIgnoreCaseAndActivoTrue(name).orElse(null);return u!=null&&u.getFiscalizador()!=null&&p.getFiscalizador()!=null&&u.getFiscalizador().getId().equals(p.getFiscalizador().getId());}
    private void requireOwner(ProgramacionFiscalizacion p,String name){requireOwner(p,usuario(name));}
    private void requireOwner(ProgramacionFiscalizacion p,AppUser u){validarPropiedad(p,u);}
    private GpsPoint gps(EstadoGps requested,BigDecimal lat,BigDecimal lon){
        if(lat==null&&lon==null)return new GpsPoint(null,null,requested==EstadoGps.NO_AUTORIZADO?EstadoGps.NO_AUTORIZADO:EstadoGps.NO_DISPONIBLE);
        if(lat==null||lon==null||lat.compareTo(BigDecimal.valueOf(-90))<0||lat.compareTo(BigDecimal.valueOf(90))>0||lon.compareTo(BigDecimal.valueOf(-180))<0||lon.compareTo(BigDecimal.valueOf(180))>0)
            throw new ConflictException("Las coordenadas GPS deben venir juntas y dentro de rango.");
        if(requested!=EstadoGps.DISPONIBLE)throw new ConflictException("Las coordenadas solo se guardan cuando el navegador informa GPS disponible.");
        return new GpsPoint(lat,lon,EstadoGps.DISPONIBLE);
    }
    private String blank(String s){return s==null||s.isBlank()?null:s.trim();}
    private LocalDateTime now(){return LocalDateTime.now(LIMA);}
    public record FileDownload(Resource resource,String filename,String contentType){}
    private record GpsPoint(BigDecimal lat,BigDecimal lon,EstadoGps status){}
}

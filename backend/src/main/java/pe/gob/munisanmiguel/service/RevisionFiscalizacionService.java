package pe.gob.munisanmiguel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.gob.munisanmiguel.dto.ApiDtos.*;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.exception.ConflictException;
import pe.gob.munisanmiguel.exception.NotFoundException;
import pe.gob.munisanmiguel.repository.*;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
public class RevisionFiscalizacionService {
    private static final ZoneId LIMA=ZoneId.of("America/Lima");
    private final RevisionFiscalizacionRepository revisions;
    private final SolicitudAclaracionFiscalizacionRepository aclaraciones;
    private final DiligenciaFiscalizacionRepository diligencias;
    private final ExpedienteFiscalizacionRepository expedientes;
    private final AppUserRepository users;
    private final ExpedienteHistorialRepository historial;
    private final ProgramacionFiscalizacionRepository programaciones;
    private final EvidenciaFiscalizacionRepository evidencias;
    private final FiscalizadorRepository fiscalizadores;
    private final FiscalizacionFileStorageService storage;
    private final ObjectMapper json;

    public RevisionFiscalizacionService(RevisionFiscalizacionRepository revisions,SolicitudAclaracionFiscalizacionRepository aclaraciones,
            DiligenciaFiscalizacionRepository diligencias,ExpedienteFiscalizacionRepository expedientes,AppUserRepository users,
            ExpedienteHistorialRepository historial,ProgramacionFiscalizacionRepository programaciones,EvidenciaFiscalizacionRepository evidencias,
            FiscalizadorRepository fiscalizadores,
            FiscalizacionFileStorageService storage,ObjectMapper json) {
        this.revisions=revisions;this.aclaraciones=aclaraciones;this.diligencias=diligencias;this.expedientes=expedientes;this.users=users;
        this.historial=historial;this.programaciones=programaciones;this.evidencias=evidencias;this.fiscalizadores=fiscalizadores;this.storage=storage;this.json=json;
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> pendientes(){
        var rows=new ArrayList<Map<String,Object>>();
        for(var d:diligencias.findAllByEstadoOrderByFechaCierreAsc(EstadoDiligencia.FINALIZADA)){
            var rev=revisions.findByDiligenciaId(d.getId()).orElse(null);
            if(rev!=null&&rev.getEstadoRevision()==EstadoRevision.FINALIZADA)continue;
            var e=d.getExpediente();var p=d.getProgramacion();var m=base(d);
            m.put("estadoRevision",rev==null?EstadoRevision.PENDIENTE.name():rev.getEstadoRevision().name());
            m.put("revisionId",rev==null?null:rev.getId());m.put("revisor",rev==null||rev.getRevisor()==null?null:rev.getRevisor().getDisplayName());
            m.put("antiguedadHoras",Duration.between(d.getFechaCierre(),now()).toHours());
            m.put("resultado","REALIZADA");m.put("cantidadEvidencias",d.getEvidencias().size());m.put("actaCodigo",d.getActa().getCodigo());
            m.put("fecha",p.getFecha());m.put("fiscalizador",d.getFiscalizadorResponsable().getNombre());m.put("origen",e.getOrigen().name());
            rows.add(m);
        }
        rows.sort(Comparator.comparing(m->Objects.toString(m.get("fechaCierre"),"")));
        return rows;
    }

    @Transactional
    public Map<String,Object> iniciar(Long diligenciaId,String username){
        var d=diligencias.findByIdForUpdate(diligenciaId).orElseThrow(()->new NotFoundException("Diligencia no encontrada."));
        if(d.getEstado()!=EstadoDiligencia.FINALIZADA||d.getActa()==null)throw new ConflictException("Solo se revisan diligencias realizadas con acta generada.");
        var user=usuario(username);var rev=revisions.findByDiligenciaId(diligenciaId).orElse(null);
        if(rev==null){rev=new RevisionFiscalizacion();rev.setExpediente(d.getExpediente());rev.setDiligencia(d);rev.setActa(d.getActa());rev.setEstadoRevision(EstadoRevision.PENDIENTE);rev.setCreadoEn(now());}
        if(rev.getEstadoRevision()==EstadoRevision.PENDIENTE){rev.setRevisor(user);rev.setFechaInicioRevision(now());rev.setEstadoRevision(EstadoRevision.EN_REVISION);rev.setActualizadoEn(now());revisions.saveAndFlush(rev);evento(rev,"REVISION_INICIADA","Revisión municipal iniciada.",username);}
        else if(rev.getEstadoRevision()==EstadoRevision.REQUIERE_ACLARACION&& !aclaraciones.existsByRevisionIdAndEstado(rev.getId(),EstadoAclaracion.PENDIENTE)){
            rev.setEstadoRevision(EstadoRevision.EN_REVISION);rev.setActualizadoEn(now());evento(rev,"REVISION_REANUDADA","Revisión retomada tras la respuesta a aclaración.",username);
        }else if(rev.getEstadoRevision()!=EstadoRevision.EN_REVISION)throw new ConflictException("La revisión no puede iniciarse en su estado actual.");
        return detalle(rev);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> expediente(String codigo){
        var e=expedientes.findByCodigo(codigo).orElseThrow(()->new NotFoundException("Expediente no encontrado."));
        var ciclos=new ArrayList<Map<String,Object>>();for(var d:diligencias.findAllByExpedienteIdOrderByFechaInicioDesc(e.getId())){
            var m=base(d);var rev=revisions.findByDiligenciaId(d.getId()).orElse(null);m.put("revision",rev==null?null:revisionMap(rev));
            if(rev!=null)m.put("aclaraciones",aclaraciones.findAllByRevisionIdOrderByFechaSolicitudAsc(rev.getId()).stream().map(this::aclaracionMap).toList());
            ciclos.add(m);
        }
        Collections.reverse(ciclos);return Map.of("expediente",Map.of("codigo",e.getCodigo(),"estado",e.getEstado().name(),"objeto",e.getObjetoFiscalizacion()),"ciclos",ciclos);
    }

    @Transactional(readOnly=true) public Map<String,Object> obtener(Long id){return detalle(revision(id));}

    @Transactional public Map<String,Object> solicitar(Long id,SolicitarAclaracionRequest req,String username){
        var rev=revisionLocked(id);editable(rev);var a=new SolicitudAclaracionFiscalizacion();a.setRevision(rev);a.setMensaje(req.mensaje().trim());a.setSolicitadaPor(usuario(username));a.setFechaSolicitud(now());a.setEstado(EstadoAclaracion.PENDIENTE);aclaraciones.save(a);
        rev.setEstadoRevision(EstadoRevision.REQUIERE_ACLARACION);rev.setActualizadoEn(now());evento(rev,"ACLARACION_SOLICITADA","Se solicitó aclaración al fiscalizador responsable.",username);
        return aclaracionMap(a);
    }

    @Transactional public Map<String,Object> responder(Long id,ResponderAclaracionRequest req,String username){
        var a=aclaraciones.findById(id).orElseThrow(()->new NotFoundException("Solicitud de aclaración no encontrada."));
        var user=usuario(username);var own=user.getFiscalizador()!=null&&user.getFiscalizador().getId().equals(a.getRevision().getDiligencia().getFiscalizadorResponsable().getId());
        if(!own)throw new org.springframework.security.access.AccessDeniedException("La aclaración pertenece a otra diligencia.");
        if(a.getEstado()!=EstadoAclaracion.PENDIENTE)throw new ConflictException("La aclaración ya fue respondida o cerrada.");
        a.setRespuesta(req.respuesta().trim());a.setRespondidaPor(user);a.setFechaRespuesta(now());a.setEstado(EstadoAclaracion.RESPONDIDA);evento(a.getRevision(),"ACLARACION_RESPONDIDA","El fiscalizador agregó una respuesta complementaria; el acta permanece intacta.",username);return aclaracionMap(a);
    }

    @Transactional public Map<String,Object> concluir(Long id,ConcluirRevisionRequest req,String username){
        var rev=revisionLocked(id);editable(rev);if(aclaraciones.existsByRevisionIdAndEstado(id,EstadoAclaracion.PENDIENTE))throw new ConflictException("Hay una aclaración pendiente de respuesta.");
        if(hayOtraRevisionPendiente(rev))throw new ConflictException("El expediente tiene otra revisión o aclaración pendiente.");
        String desc=blank(req.descripcionConclusion());String otra=blank(req.denominacionOtra());
        if(req.conclusion()!=ConclusionFiscalizacion.CONFORMIDAD&&desc==null&&req.conclusion()!=ConclusionFiscalizacion.RECOMIENDA_INICIO_PROCEDIMIENTO)
            throw new ConflictException("Describa la recomendación, advertencia o medida que registra MUNICIPIO.");
        if(req.conclusion()==ConclusionFiscalizacion.OTRA&&otra==null)throw new ConflictException("Indique la denominación de la otra conclusión.");
        if(req.conclusion()==ConclusionFiscalizacion.RECOMIENDA_INICIO_PROCEDIMIENTO&&!req.requiereDerivacion())throw new ConflictException("Esta conclusión debe registrar derivación pendiente.");
        if(req.conclusion()==ConclusionFiscalizacion.CONFORMIDAD&&req.requiereSeguimiento())throw new ConflictException("La conformidad no puede generar seguimiento.");
        rev.setConclusion(req.conclusion());rev.setFundamentoConclusion(req.fundamentoConclusion().trim());rev.setObservacionesRevision(blank(req.observacionesRevision()));rev.setDescripcionConclusion(desc);rev.setPlazoConclusion(blank(req.plazoConclusion()));rev.setRequiereSeguimiento(req.requiereSeguimiento());rev.setRequiereDerivacion(req.conclusion()==ConclusionFiscalizacion.RECOMIENDA_INICIO_PROCEDIMIENTO&&req.requiereDerivacion());rev.setDenominacionOtra(otra);rev.setReferenciaDocumentos(blank(req.referenciaDocumentos()));rev.setEstadoRevision(EstadoRevision.FINALIZADA);rev.setFechaFinalRevision(now());rev.setActualizadoEn(now());
        evento(rev,"REVISION_FINALIZADA","Revisión finalizada.",username);evento(rev,"CONCLUSION_REGISTRADA","Conclusión: "+req.conclusion().name(),username);
        if(rev.isRequiereDerivacion())evento(rev,"DERIVACION_RECOMENDADA","Se registró una derivación pendiente para evaluación posterior; no se creó un procedimiento sancionador.",username);
        if(rev.isRequiereSeguimiento()){rev.getExpediente().setEstado(EstadoExpediente.EN_SEGUIMIENTO);evento(rev,"SEGUIMIENTO_REQUERIDO","La conclusión requiere seguimiento.",username);}
        else {if(tieneProgramacionActiva(rev.getExpediente()))throw new ConflictException("No se puede cerrar mientras exista una programación activa.");rev.getExpediente().setEstado(EstadoExpediente.CERRADO);evento(rev,"EXPEDIENTE_CERRADO","Expediente cerrado por conclusión municipal.",username);}
        generarInforme(rev);return detalle(rev);
    }

    @Transactional public Map<String,Object> seguimiento(Long id,SeguimientoRequest req,String username){
        var rev=revision(id);if(rev.getEstadoRevision()!=EstadoRevision.FINALIZADA||!rev.isRequiereSeguimiento()||rev.getExpediente().getEstado()!=EstadoExpediente.EN_SEGUIMIENTO)throw new ConflictException("El expediente no tiene seguimiento pendiente habilitado.");
        if(programaciones.existsByRevisionOrigen_Id(id))throw new ConflictException("Esta revisión ya tiene una programación de seguimiento asociada.");
        if(req.fecha().isBefore(LocalDate.now(LIMA))||LocalDateTime.of(req.fecha(),req.hora()).isBefore(now()))throw new ConflictException("La fecha y hora del seguimiento deben ser actuales o futuras.");
        var p=new ProgramacionFiscalizacion();p.setExpediente(rev.getExpediente());p.setFiscalizador(fiscalizador(req.fiscalizadorId()));if(!p.getFiscalizador().isActivo())throw new ConflictException("El fiscalizador está inactivo.");p.setFecha(req.fecha());p.setHora(req.hora());if(programaciones.existsByFiscalizadorIdAndFechaAndHoraAndEstadoProgramacionIn(req.fiscalizadorId(),req.fecha(),req.hora(),List.of(EstadoProgramacion.PROGRAMADA,EstadoProgramacion.EN_CURSO)))throw new ConflictException("El fiscalizador ya tiene una visita activa para esa fecha y hora.");p.setTipoVisita(req.tipoVisita());p.setEstadoProgramacion(EstadoProgramacion.PROGRAMADA);p.setFechaCreacion(now());p.setCreadaPor(usuario(username));p.setMotivoSeguimiento(req.motivo().trim());p.setRevisionOrigen(rev);programaciones.saveAndFlush(p);
        var h=evento(rev,"PROGRAMACION_SEGUIMIENTO_CREADA","Programación de seguimiento creada: "+req.motivo().trim(),username);h.setProgramacion(p);
        return Map.of("id",p.getId(),"expedienteCodigo",rev.getExpediente().getCodigo(),"fecha",p.getFecha(),"hora",p.getHora(),"tipoVisita",p.getTipoVisita().name(),"fiscalizador",p.getFiscalizador().getNombre(),"motivo",p.getMotivoSeguimiento());
    }

    @Transactional(readOnly=true) public FileDownload informe(Long id){var r=revision(id);if(r.getInformePdfInterno()==null)throw new NotFoundException("El informe todavía no está generado.");var file=storage.load(r.getInformePdfInterno());if(file==null)throw new NotFoundException("El PDF del informe no está disponible.");return new FileDownload(file,r.getInformePdfNombre());}
    @Transactional(readOnly=true) public List<Map<String,Object>> misAclaraciones(String username){var u=usuario(username);if(u.getFiscalizador()==null)throw new NotFoundException("El usuario no tiene fiscalizador asociado.");return aclaraciones.findAllByEstadoAndRevisionDiligenciaFiscalizadorResponsableIdOrderByFechaSolicitudAsc(EstadoAclaracion.PENDIENTE,u.getFiscalizador().getId()).stream().map(a->{var m=aclaracionMap(a);m.put("expedienteCodigo",a.getRevision().getExpediente().getCodigo());m.put("diligenciaId",a.getRevision().getDiligencia().getId());return m;}).toList();}
    @Transactional(readOnly=true) public FileDownload evidencia(Long diligenciaId,Long evidenciaId){var e=evidencias.findByIdAndDiligenciaId(evidenciaId,diligenciaId).orElseThrow(()->new NotFoundException("Evidencia no encontrada."));var f=storage.load(e.getNombreInterno());if(f==null)throw new NotFoundException("Archivo de evidencia no disponible.");return new FileDownload(f,e.getNombreOriginal(),e.getTipoMime());}

    private Map<String,Object> detalle(RevisionFiscalizacion r){var m=base(r.getDiligencia());m.put("revision",revisionMap(r));m.put("acta",actaMap(r.getActa()));m.put("aclaraciones",aclaraciones.findAllByRevisionIdOrderByFechaSolicitudAsc(r.getId()).stream().map(this::aclaracionMap).toList());return m;}
    private Map<String,Object> base(DiligenciaFiscalizacion d){var e=d.getExpediente();var p=d.getProgramacion();var m=new LinkedHashMap<String,Object>();m.put("diligenciaId",d.getId());m.put("expedienteCodigo",e.getCodigo());m.put("origen",e.getOrigen().name());m.put("objeto",e.getObjetoFiscalizacion());m.put("administrado",String.join(" ",Arrays.asList(Objects.toString(e.getNombreAdministrado(),""),Objects.toString(e.getAdministradoApellidos(),"")).stream().filter(s->!s.isBlank()).toList()));m.put("documento",e.getDocumentoAdministrado());m.put("direccion",e.getDireccionFiscalizada());m.put("fechaInicio",d.getFechaInicio());m.put("fechaCierre",d.getFechaCierre());m.put("lugar",d.getLugarDiligencia());m.put("tipoVisita",p.getTipoVisita().name());m.put("fechaProgramada",p.getFecha());m.put("horaProgramada",p.getHora());m.put("fiscalizador",d.getFiscalizadorResponsable().getNombre());m.put("hechos",d.getHechosConstatados());m.put("ocurrencias",d.getOcurrencias());m.put("observacionesFiscalizador",d.getObservacionesFiscalizador());m.put("observacionesFiscalizado",d.getObservacionesFiscalizado());m.put("personaPresente",d.getPersonaNombres()==null?"":Objects.toString(d.getPersonaNombres(),"")+" "+Objects.toString(d.getPersonaApellidos(),""));m.put("situacionPersona",d.getSituacionPersona().name());m.put("actaCodigo",d.getActa()==null?null:d.getActa().getCodigo());m.put("participantes",d.getParticipantes().stream().map(x->Map.of("tipo",x.getTipoParticipante().name(),"nombre",x.getNombre()+" "+Objects.toString(x.getApellidos(),""),"cargo",Objects.toString(x.getCargoCondicion(),""))).toList());m.put("evidencias",d.getEvidencias().stream().map(x->Map.of("id",x.getId(),"tipo",x.getTipo().name(),"nombre",x.getNombreOriginal(),"mime",x.getTipoMime(),"descripcion",Objects.toString(x.getDescripcion(),""),"autor",x.getCreadoPor()==null?"":x.getCreadoPor().getDisplayName(),"fecha",x.getFechaHora())).toList());return m;}
    private Map<String,Object> actaMap(ActaFiscalizacion a){try{var x=new LinkedHashMap<String,Object>();x.put("id",a.getId());x.put("codigo",a.getCodigo());x.put("generadoEn",a.getGeneradoEn());x.put("pdfGenerado",a.getPdfGeneradoNombre());x.put("pdfFirmado",a.getPdfFirmadoNombre());x.put("firmaEstado",a.getFirmaFiscalizadoEstado().name());x.put("firmaObservacion",a.getFirmaFiscalizadoObservacion());x.put("contenido",json.readValue(a.getContenidoJson(),new TypeReference<Map<String,Object>>(){}));return x;}catch(Exception ex){throw new IllegalStateException(ex);}}
    private Map<String,Object> revisionMap(RevisionFiscalizacion r){var m=new LinkedHashMap<String,Object>();m.put("id",r.getId());m.put("estado",r.getEstadoRevision().name());m.put("revisor",r.getRevisor()==null?null:r.getRevisor().getDisplayName());m.put("fechaInicio",r.getFechaInicioRevision());m.put("fechaFinal",r.getFechaFinalRevision());m.put("conclusion",r.getConclusion()==null?null:r.getConclusion().name());m.put("fundamento",r.getFundamentoConclusion());m.put("observaciones",r.getObservacionesRevision());m.put("descripcion",r.getDescripcionConclusion());m.put("plazo",r.getPlazoConclusion());m.put("requiereSeguimiento",r.isRequiereSeguimiento());m.put("requiereDerivacion",r.isRequiereDerivacion());m.put("otra",r.getDenominacionOtra());m.put("documentos",r.getReferenciaDocumentos());m.put("informe",r.getInformePdfNombre());m.put("checksum",r.getInformeChecksum());m.put("programacionSeguimiento",programaciones.findAllByRevisionOrigen_IdOrderByIdAsc(r.getId()).stream().map(p->Map.of("id",p.getId(),"fecha",p.getFecha(),"hora",p.getHora(),"estado",p.getEstadoProgramacion().name(),"tipoVisita",p.getTipoVisita().name(),"fiscalizador",p.getFiscalizador().getNombre(),"motivo",p.getMotivoSeguimiento())).toList());return m;}
    private Map<String,Object> aclaracionMap(SolicitudAclaracionFiscalizacion a){var m=new LinkedHashMap<String,Object>();m.put("id",a.getId());m.put("mensaje",a.getMensaje());m.put("solicitadaPor",a.getSolicitadaPor().getDisplayName());m.put("fechaSolicitud",a.getFechaSolicitud());m.put("respondidaPor",a.getRespondidaPor()==null?null:a.getRespondidaPor().getDisplayName());m.put("fechaRespuesta",a.getFechaRespuesta());m.put("respuesta",a.getRespuesta());m.put("estado",a.getEstado().name());return m;}
    private RevisionFiscalizacion revision(Long id){return revisions.findById(id).orElseThrow(()->new NotFoundException("Revisión no encontrada."));}
    private RevisionFiscalizacion revisionLocked(Long id){return revisions.findLocked(id).orElseThrow(()->new NotFoundException("Revisión no encontrada."));}
    private void editable(RevisionFiscalizacion r){if(r.getEstadoRevision()!=EstadoRevision.EN_REVISION)throw new ConflictException("La revisión no está abierta para cambios.");}
    private boolean tieneProgramacionActiva(ExpedienteFiscalizacion e){return programaciones.findAllByExpedienteIdOrderByIdAsc(e.getId()).stream().anyMatch(p->p.getEstadoProgramacion()==EstadoProgramacion.PROGRAMADA||p.getEstadoProgramacion()==EstadoProgramacion.EN_CURSO);}
    private boolean hayOtraRevisionPendiente(RevisionFiscalizacion actual){return diligencias.findAllByExpedienteIdOrderByFechaInicioDesc(actual.getExpediente().getId()).stream().map(d->revisions.findByDiligenciaId(d.getId()).orElse(null)).anyMatch(r->r!=null&&!Objects.equals(r.getId(),actual.getId())&&(r.getEstadoRevision()==EstadoRevision.PENDIENTE||r.getEstadoRevision()==EstadoRevision.EN_REVISION||r.getEstadoRevision()==EstadoRevision.REQUIERE_ACLARACION));}
    private AppUser usuario(String name){return users.findByUsernameIgnoreCaseAndActivoTrue(name).orElseThrow(()->new NotFoundException("Usuario no encontrado."));}
    private Fiscalizador fiscalizador(String id){return fiscalizadores.findById(id).orElseThrow(()->new NotFoundException("Fiscalizador no encontrado."));}
    private LocalDateTime now(){return LocalDateTime.now(LIMA);}
    private String blank(String s){return s==null||s.isBlank()?null:s.trim();}
    private ExpedienteHistorial evento(RevisionFiscalizacion r,String tipo,String descripcion,String username){var h=new ExpedienteHistorial();h.setExpediente(r.getExpediente());h.setProgramacion(r.getDiligencia().getProgramacion());h.setFechaHora(now());h.setActorUsername(username);h.setActor(usuario(username));h.setTipoEvento(tipo);h.setDescripcion(descripcion);historial.save(h);return h;}
    private void generarInforme(RevisionFiscalizacion r){
        String txt="MUNICIPALIDAD DISTRITAL DE SAN MIGUEL\nINFORME DE CONCLUSION DE FISCALIZACION\nExpediente: "+r.getExpediente().getCodigo()+"\nActa: "+r.getActa().getCodigo()+" / diligencia "+r.getDiligencia().getId()+"\nRevisor: "+r.getRevisor().getDisplayName()+"\nFecha: "+r.getFechaFinalRevision()+"\nConclusion: "+r.getConclusion()+"\nFundamento: "+r.getFundamentoConclusion()+"\nDetalle: "+Objects.toString(r.getDescripcionConclusion(),"No consignado")+"\nSeguimiento requerido: "+r.isRequiereSeguimiento()+"\nDerivacion recomendada: "+r.isRequiereDerivacion()+"\nEsta recomendacion no determina responsabilidad ni impone sancion.";
        byte[] pdf=PdfOnePage.create(txt);String name="informe-conclusion-"+r.getExpediente().getCodigo()+"-revision-"+r.getId()+".pdf";var stored=storage.storeGeneratedPdf(pdf,name);r.setInformePdfNombre(name);r.setInformePdfInterno(stored.internalName());try{r.setInformeChecksum(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)));}catch(Exception e){throw new IllegalStateException(e);}evento(r,"INFORME_CONCLUSION_GENERADO","PDF de conclusión generado con checksum SHA-256.",r.getRevisor().getUsername());
    }
    public record FileDownload(Resource resource,String filename,String mime){public FileDownload(Resource r,String f){this(r,f,"application/pdf");}}
    private static final class PdfOnePage {
        private static final Charset C=Charset.forName("windows-1252");
        static byte[] create(String text){try{StringBuilder content=new StringBuilder("BT /F1 10 Tf 48 790 Td 14 TL\n");for(String line:text.split("\\R")){content.append('(').append(escape(line)).append(") Tj T*\n");}content.append("ET");byte[] s=content.toString().getBytes(C);String stream=new String(s,C);String[] objs={"<< /Type /Catalog /Pages 2 0 R >>","<< /Type /Pages /Kids [3 0 R] /Count 1 >>","<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>","<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>","<< /Length "+s.length+" >>\nstream\n"+stream+"\nendstream"};StringBuilder out=new StringBuilder("%PDF-1.4\n");List<Integer> offsets=new ArrayList<>();offsets.add(0);for(int i=0;i<objs.length;i++){offsets.add(out.toString().getBytes(C).length);out.append(i+1).append(" 0 obj\n").append(objs[i]).append("\nendobj\n");}int xref=out.toString().getBytes(C).length;out.append("xref\n0 6\n0000000000 65535 f \n");for(int i=1;i<offsets.size();i++)out.append(String.format(Locale.ROOT,"%010d 00000 n \n",offsets.get(i)));out.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");return out.toString().getBytes(C);}catch(Exception e){throw new IllegalStateException(e);}}
        static String escape(String s){return s.replace("\\","\\\\").replace("(","\\(").replace(")","\\)").replaceAll("[^\\x20-\\x7E]","?");}
    }
}

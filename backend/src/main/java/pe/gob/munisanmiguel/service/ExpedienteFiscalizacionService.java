package pe.gob.munisanmiguel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.gob.munisanmiguel.dto.ApiDtos.*;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.exception.ConflictException;
import pe.gob.munisanmiguel.exception.NotFoundException;
import pe.gob.munisanmiguel.repository.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class ExpedienteFiscalizacionService {
    private static final ZoneId LIMA=ZoneId.of("America/Lima");
    private final ExpedienteFiscalizacionRepository expedientes;
    private final ProgramacionFiscalizacionRepository programaciones;
    private final DiligenciaFiscalizacionRepository diligencias;
    private final ExpedienteHistorialRepository historial;
    private final AppUserRepository users;
    private final FiscalizadorRepository fiscalizadores;
    private final SolicitudRepository solicitudes;
    private final ObjectMapper json;
    private final DocumentoStorageService documentos;

    public ExpedienteFiscalizacionService(ExpedienteFiscalizacionRepository expedientes,
            ProgramacionFiscalizacionRepository programaciones, DiligenciaFiscalizacionRepository diligencias,
            ExpedienteHistorialRepository historial,
            AppUserRepository users, FiscalizadorRepository fiscalizadores, SolicitudRepository solicitudes,
            ObjectMapper json, DocumentoStorageService documentos) {
        this.expedientes=expedientes; this.programaciones=programaciones; this.diligencias=diligencias; this.historial=historial;
        this.users=users; this.fiscalizadores=fiscalizadores; this.solicitudes=solicitudes; this.json=json; this.documentos=documentos;
    }

    @Transactional
    public List<ExpedienteResponse> listar() {
        return expedientes.findAllByOrderByFechaCreacionDescIdDesc().stream().map(this::response).toList();
    }

    @Transactional
    public ExpedienteResponse obtener(String codigo) { vencerProgramaciones(); return response(entidad(codigo)); }

    @Transactional
    public ExpedienteResponse adjuntarDocumento(String codigo, MultipartFile file, String username) {
        var e=entidad(codigo); var stored=documentos.store(file);
        if(e.getDocumentoSustentoInterno()!=null && e.getSolicitud()==null) documentos.delete(e.getDocumentoSustentoInterno());
        e.setDocumentoSustentoNombre(stored.originalName());e.setDocumentoSustentoInterno(stored.internalName());
        e.setDocumentoSustentoTipo(stored.contentType());e.setDocumentoSustentoFecha(stored.uploadedAt());
        evento(e,null,"DOCUMENTO_SUSTENTO_ADJUNTADO","Se adjuntó documento de sustento",null,map("nombre",stored.originalName()),username);
        return response(e);
    }

    @Transactional(readOnly=true)
    public org.springframework.core.io.Resource documento(String codigo) {
        var e=expedientes.findByCodigo(codigo).orElseThrow(()->new NotFoundException("Expediente no encontrado: "+codigo));
        if(e.getSolicitud()!=null && e.getDocumentoSustentoInterno()==null) return documentos.load(e.getSolicitud().getDocumentoNombreInterno());
        return documentos.load(e.getDocumentoSustentoInterno());
    }

    @Transactional
    public ExpedienteResponse crear(ExpedienteRequest r, String username) {
        validarDatosAdministrado(r);
        var e=new ExpedienteFiscalizacion(); e.setCodigo(codigoTemporal());
        e.setFechaCreacion(LocalDateTime.now(LIMA)); e.setCreadoPor(usuario(username)); e.setOrigen(r.origen());
        e.setDetalleOrigen(blank(r.detalleOrigen())); e.setEstado(EstadoExpediente.ABIERTO);
        e.setObjetoFiscalizacion(r.objetoFiscalizacion().trim()); e.setObservacionesIniciales(blank(r.observacionesIniciales()));
        e.setTipoAdministrado(blank(r.tipoAdministrado())); e.setDocumentoAdministrado(blank(r.documentoAdministrado()));
        e.setNombreAdministrado(blank(r.nombreAdministrado())); e.setTelefonoContacto(blank(r.telefonoContacto()));
        e.setDireccionFiscalizada(blank(r.direccionFiscalizada()));
        e.setAdministradoApellidos(blank(r.administradoApellidos())); e.setReferenciaDomicilio(blank(r.referenciaDomicilio()));
        e.setLatitud(r.latitud()); e.setLongitud(r.longitud());
        if (r.solicitudCodigo()!=null&&!r.solicitudCodigo().isBlank())
            e.setSolicitud(solicitudes.findByCodigo(r.solicitudCodigo()).orElseThrow(()->new NotFoundException("Solicitud no encontrada.")));
        expedientes.saveAndFlush(e); e.setCodigo("FIS-"+e.getFechaCreacion().getYear()+"-"+String.format("%05d",e.getId()));
        expedientes.save(e); evento(e,null,"EXPEDIENTE_CREADO","Expediente creado",null,map("origen",e.getOrigen().name(),"estado",e.getEstado().name()),username);
        return response(e);
    }

    @Transactional
    public ExpedienteResponse editar(String codigo,ExpedienteRequest r,String username){
        validarDatosAdministrado(r);
        var e=entidad(codigo);asegurarNoCerrado(e);var antes=map("origen",e.getOrigen().name(),"detalleOrigen",e.getDetalleOrigen(),"objetoFiscalizacion",e.getObjetoFiscalizacion(),"observacionesIniciales",e.getObservacionesIniciales());
        if(r.solicitudCodigo()!=null&&!Objects.equals(r.solicitudCodigo(),e.getSolicitud()==null?null:e.getSolicitud().getCodigo()))
            throw new ConflictException("La solicitud vinculada no puede cambiarse luego de crear el expediente.");
        e.setOrigen(r.origen());e.setDetalleOrigen(blank(r.detalleOrigen()));e.setObjetoFiscalizacion(r.objetoFiscalizacion().trim());
        e.setObservacionesIniciales(blank(r.observacionesIniciales()));e.setTipoAdministrado(blank(r.tipoAdministrado()));
        e.setDocumentoAdministrado(blank(r.documentoAdministrado()));e.setNombreAdministrado(blank(r.nombreAdministrado()));
        e.setTelefonoContacto(blank(r.telefonoContacto()));e.setDireccionFiscalizada(blank(r.direccionFiscalizada()));
        e.setAdministradoApellidos(blank(r.administradoApellidos())); e.setReferenciaDomicilio(blank(r.referenciaDomicilio()));
        e.setLatitud(r.latitud()); e.setLongitud(r.longitud());
        evento(e,null,"EXPEDIENTE_EDITADO","Datos del expediente actualizados",antes,map("origen",e.getOrigen().name(),"detalleOrigen",e.getDetalleOrigen(),"objetoFiscalizacion",e.getObjetoFiscalizacion(),"observacionesIniciales",e.getObservacionesIniciales()),username);
        return response(e);
    }

    @Transactional
    public ProgramacionFiscalizacionResponse programar(String codigo, ProgramacionRequest r, String username) {
        if(r.tipoVisita()==TipoVisita.PROGRAMADA) validarFechaFutura(r.fecha(), r.hora());
        else if(r.fecha()==null||r.hora()==null) throw new ConflictException("Indique la fecha y hora operativa de la visita.");
        if(r.fiscalizadorId()==null||r.fiscalizadorId().isBlank()) throw new ConflictException("Debe asignar un fiscalizador activo.");
        var e=entidad(codigo);asegurarNoCerrado(e); var actor=usuario(username);
        var p=new ProgramacionFiscalizacion(); p.setExpediente(e); p.setTipoVisita(r.tipoVisita());
        p.setFecha(r.fecha());p.setHora(r.hora());p.setEstadoProgramacion(EstadoProgramacion.PROGRAMADA);
        p.setFechaCreacion(LocalDateTime.now(LIMA));p.setCreadaPor(actor);p.setFiscalizador(fiscalizador(r.fiscalizadorId()));
        validarDisponibilidad(p.getFiscalizador(),p.getFecha(),p.getHora()); programaciones.save(p);
        copiarProgramacionALegacy(e,p);
        evento(e,p,"PROGRAMACION_CREADA","Visita programada",null,map("fecha",p.getFecha(),"hora",p.getHora(),"estado",p.getEstadoProgramacion().name()),username);
        return response(p);
    }

    @Transactional
    public ProgramacionFiscalizacionResponse reprogramar(Long id, ReprogramarRequest r, String username) {
        if(r.tipoVisita()==TipoVisita.PROGRAMADA) validarFechaFutura(r.fecha(), r.hora());
        else if(r.fecha()==null||r.hora()==null) throw new ConflictException("Indique la fecha y hora operativa de la visita.");
        var anterior=programaciones.findById(id).orElseThrow(()->new NotFoundException("Programación no encontrada."));
        asegurarNoCerrado(anterior.getExpediente());
        if(anterior.getEstadoProgramacion()!=EstadoProgramacion.PROGRAMADA&&anterior.getEstadoProgramacion()!=EstadoProgramacion.VENCIDA)
            throw new ConflictException("Solo se puede reprogramar una visita programada o vencida.");
        var actor=usuario(username); var old=map("fecha",anterior.getFecha(),"hora",anterior.getHora(),"estado",anterior.getEstadoProgramacion().name());
        anterior.setEstadoProgramacion(EstadoProgramacion.REPROGRAMADA);
        var nueva=new ProgramacionFiscalizacion();nueva.setExpediente(anterior.getExpediente());nueva.setFiscalizador(fiscalizador(r.fiscalizadorId()==null?anterior.getFiscalizador()==null?null:anterior.getFiscalizador().getId():r.fiscalizadorId()));
        nueva.setFecha(r.fecha());nueva.setHora(r.hora());nueva.setTipoVisita(r.tipoVisita());nueva.setEstadoProgramacion(EstadoProgramacion.PROGRAMADA);
        nueva.setCreadaPor(actor);nueva.setFechaCreacion(LocalDateTime.now(LIMA));nueva.setMotivoReprogramacion(r.motivo().trim());nueva.setProgramacionAnterior(anterior);
        validarDisponibilidad(nueva.getFiscalizador(),nueva.getFecha(),nueva.getHora());programaciones.save(nueva);
        copiarProgramacionALegacy(nueva.getExpediente(),nueva);
        evento(nueva.getExpediente(),anterior,"PROGRAMACION_REPROGRAMADA","Programación sustituida: "+r.motivo().trim(),old,map("estado","REPROGRAMADA","nuevaProgramacionId",nueva.getId()),username);
        evento(nueva.getExpediente(),nueva,"PROGRAMACION_CREADA_POR_REPROGRAMACION","Nueva visita programada",null,map("fecha",nueva.getFecha(),"hora",nueva.getHora(),"estado","PROGRAMADA","programacionAnteriorId",anterior.getId()),username);
        return response(nueva);
    }

    @Transactional
    public ProgramacionFiscalizacionResponse cancelar(Long id, CancelarProgramacionRequest r, String username) {
        var p=programaciones.findById(id).orElseThrow(()->new NotFoundException("Programación no encontrada."));
        asegurarNoCerrado(p.getExpediente());
        if(p.getEstadoProgramacion()!=EstadoProgramacion.PROGRAMADA&&p.getEstadoProgramacion()!=EstadoProgramacion.VENCIDA)
            throw new ConflictException("Solo se puede cancelar una visita programada o vencida.");
        String antes=p.getEstadoProgramacion().name();p.setEstadoProgramacion(EstadoProgramacion.CANCELADA);p.setMotivoCancelacion(r.motivo().trim());
        if(p.getExpediente().getSolicitud()!=null){var s=p.getExpediente().getSolicitud();s.setFechaFiscalizacion(null);s.setHoraFiscalizacion(null);s.setFiscalizador(null);}
        evento(p.getExpediente(),p,"PROGRAMACION_CANCELADA","Programación cancelada: "+r.motivo().trim(),map("estado",antes),map("estado","CANCELADA"),username);
        return response(p);
    }

    @Transactional
    public List<ProgramacionFiscalizacionResponse> misProgramaciones(String username) {
        vencerProgramaciones(); var u=usuario(username);
        if(u.getFiscalizador()==null) throw new NotFoundException("El usuario no tiene fiscalizador asociado.");
        return programaciones.findAllByFiscalizadorIdOrderByIdAsc(u.getFiscalizador().getId()).stream().map(this::response).toList();
    }

    @Transactional(readOnly=true)
    public List<FiscalizadorExpedienteTaskResponse> misTareasCampo(String username) {
        var u=usuario(username);
        if(u.getFiscalizador()==null) throw new NotFoundException("El usuario no tiene fiscalizador asociado.");
        return programaciones.findAllByFiscalizadorIdAndEstadoProgramacionInOrderByFechaAscHoraAsc(
                u.getFiscalizador().getId(), List.of(EstadoProgramacion.PROGRAMADA, EstadoProgramacion.EN_CURSO, EstadoProgramacion.VENCIDA,
                        EstadoProgramacion.REALIZADA, EstadoProgramacion.NO_REALIZADA))
                .stream().map(p -> {
                    var e=p.getExpediente();
                    String administrado=e.getNombreAdministrado();
                    if(e.getAdministradoApellidos()!=null&&!e.getAdministradoApellidos().isBlank())
                        administrado=(administrado==null?"":administrado)+" "+e.getAdministradoApellidos();
                    var d=diligencias.findByProgramacionId(p.getId()).orElse(null);
                    return new FiscalizadorExpedienteTaskResponse(e.getCodigo(),e.getObjetoFiscalizacion(),administrado,
                            e.getDocumentoAdministrado(),e.getTelefonoContacto(),e.getDireccionFiscalizada(),e.getReferenciaDomicilio(),
                            p.getFecha(),p.getHora(),e.getLatitud(),e.getLongitud(),e.getOrigen().name(),p.getTipoVisita().name(),
                            p.getEstadoProgramacion().name(),e.getEstado().name(),p.getId(),d==null?null:d.getId(),
                            d==null?null:d.getEstado().name(),e.getSolicitud()==null?null:e.getSolicitud().getCodigo());
                }).toList();
    }

    @Transactional(readOnly=true)
    public HtmlJornadaDownload jornadaHtml(String username,LocalDate requestedDate){
        var user=usuario(username);
        if(user.getFiscalizador()==null)throw new NotFoundException("El usuario no tiene fiscalizador asociado.");
        LocalDate date=requestedDate==null?LocalDate.now(LIMA):requestedDate;
        var visits=programaciones.findAllByFiscalizadorIdAndEstadoProgramacionInOrderByFechaAscHoraAsc(
                user.getFiscalizador().getId(),List.of(EstadoProgramacion.PROGRAMADA,EstadoProgramacion.VENCIDA,EstadoProgramacion.EN_CURSO))
                .stream().filter(p->date.equals(p.getFecha())).sorted(Comparator.comparing(ProgramacionFiscalizacion::getHora)).toList();
        StringBuilder rows=new StringBuilder();
        for(var p:visits){
            var e=p.getExpediente();String address=Objects.toString(e.getDireccionFiscalizada(),"").trim();
            boolean coordinates=validCoordinates(e.getLatitud(),e.getLongitud());
            String maps=coordinates?"https://www.google.com/maps/search/?api=1&query="+e.getLatitud()+","+e.getLongitud()
                    :address.isBlank()?null:"https://www.google.com/maps/search/?api=1&query="+URLEncoder.encode(address,StandardCharsets.UTF_8);
            String location=coordinates?escape(e.getLatitud()+", "+e.getLongitud()):address.isBlank()?"<strong>Sin ubicación suficiente</strong>":"Sin coordenadas; se usará la dirección";
            rows.append("<tr data-id=\"").append(p.getId()).append("\" data-lat=\"")
                    .append(coordinates?e.getLatitud():"").append("\" data-lon=\"")
                    .append(coordinates?e.getLongitud():"").append("\"><td>")
                    .append(escape(p.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))).append("</td><td>")
                    .append(escape(p.getHora().format(DateTimeFormatter.ofPattern("HH:mm"))))
                    .append("<br><small>").append(escape(horaDoce(p.getHora()))).append("</small></td><td><strong>")
                    .append(escape(e.getCodigo())).append("</strong></td><td>").append(escape(e.getObjetoFiscalizacion()))
                    .append("</td><td>").append(escape(address.isBlank()?"Sin dirección":address)).append("</td><td>")
                    .append(escape(Objects.toString(e.getReferenciaDomicilio(),"Sin referencia"))).append("</td><td>")
                    .append(location).append(maps==null?"":"<br><a href=\""+escape(maps)+"\">Abrir Google Maps</a>")
                    .append("</td><td>").append(escape(p.getEstadoProgramacion().name()))
                    .append("</td><td><button type=\"button\" data-toggle>Marcar atendida (solo esta guía)</button><br><label>Hora declarada (opcional) <input type=\"time\" data-hour></label><small data-local-state></small></td></tr>");
        }
        if(visits.isEmpty())rows.append("<tr><td colspan=\"9\">No hay visitas pendientes asignadas para esta fecha.</td></tr>");
        String guideScript=visits.isEmpty()?"":"""
                <script>
                (()=>{
                  const rows=[...document.querySelectorAll('tr[data-id]')],svg=document.getElementById('local-map');
                  const ns='http://www.w3.org/2000/svg',route=document.getElementById('route-link'),count=document.getElementById('pending-count');
                  function point(row){const lat=Number(row.dataset.lat),lon=Number(row.dataset.lon);return row.dataset.lat!==''&&row.dataset.lon!==''&&Number.isFinite(lat)&&Number.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180?{row,lat,lon}:null;}
                  function render(){const active=rows.filter(row=>row.dataset.attended!=='yes'),points=active.map(point).filter(Boolean);
                    count.textContent=String(active.length);svg.replaceChildren();
                    if(points.length){const lats=points.map(p=>p.lat),lons=points.map(p=>p.lon),minLat=Math.min(...lats),maxLat=Math.max(...lats),minLon=Math.min(...lons),maxLon=Math.max(...lons);
                      points.forEach((p,i)=>{const x=40+(p.lon-minLon)/(maxLon-minLon||1)*560,y=280-(p.lat-minLat)/(maxLat-minLat||1)*240;
                        const group=document.createElementNS(ns,'g'),circle=document.createElementNS(ns,'circle'),label=document.createElementNS(ns,'text'),title=document.createElementNS(ns,'title');
                        circle.setAttribute('cx',x);circle.setAttribute('cy',y);circle.setAttribute('r','13');circle.setAttribute('fill','#166a53');
                        label.setAttribute('x',x);label.setAttribute('y',y+5);label.setAttribute('text-anchor','middle');label.setAttribute('fill','white');label.textContent=String(i+1);
                        title.textContent=p.row.cells[2].textContent+' · '+p.row.cells[4].textContent;group.append(circle,label,title);svg.append(group);});}
                    const stops=points.map(p=>p.lat+','+p.lon);if(stops.length){const u=new URL(stops.length===1?'https://www.google.com/maps/search/':'https://www.google.com/maps/dir/');u.searchParams.set('api','1');
                      if(stops.length===1)u.searchParams.set('query',stops[0]);else{u.searchParams.set('origin',stops[0]);u.searchParams.set('destination',stops.at(-1));if(stops.length>2)u.searchParams.set('waypoints',stops.slice(1,-1).join('|'));}
                      route.href=u.href;route.hidden=false;}else route.hidden=true;
                    document.getElementById('map-note').textContent=points.length?'Vista local esquemática de '+points.length+' punto(s); no sustituye a un mapa cartográfico.':'No quedan puntos pendientes con coordenadas.';
                  }
                  rows.forEach(row=>{const button=row.querySelector('[data-toggle]'),state=row.querySelector('[data-local-state]'),hour=row.querySelector('[data-hour]');
                    button.addEventListener('click',()=>{const attended=row.dataset.attended!=='yes';row.dataset.attended=attended?'yes':'no';button.textContent=attended?'Desmarcar atención local':'Marcar atendida (solo esta guía)';state.textContent=attended?'Atendida en esta guía'+(hour.value?' a las '+hour.value:''):'';render();});
                    hour.addEventListener('change',()=>{if(row.dataset.attended==='yes')state.textContent='Atendida en esta guía'+(hour.value?' a las '+hour.value:'');});});render();
                })();
                </script>
                """;
        String html="""
                <!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Mis visitas pendientes</title><style>body{font-family:Arial,sans-serif;margin:24px;color:#17211d}table{border-collapse:collapse;width:100%%}th,td{border:1px solid #bbb;padding:8px;text-align:left;vertical-align:top}th{background:#eee}small{color:#555}svg{border:1px solid #bbb;max-width:100%%;height:auto}button,input{font:inherit}small{display:block}[data-attended=yes]{opacity:.6}@media print{a{color:#000}}</style></head>
                <body><h1>Mis visitas pendientes</h1><p><strong>Fiscalizador:</strong> %s</p><p><strong>Fecha:</strong> %s</p>
                <p>Guía de consulta generada el %s (America/Lima). Marcar una visita aquí no registra diligencias, actas ni cambios en el sistema. Las marcas y horas son solo locales durante esta sesión; un archivo file:// no garantiza conservarlas.</p>
                <p>Pendientes en esta guía: <strong id="pending-count">%s</strong> · <a id="route-link" href="https://www.google.com/maps" target="_blank" rel="noopener" hidden>Abrir recorrido pendiente en Google Maps</a></p>
                <svg id="local-map" viewBox="0 0 640 320" role="img" aria-label="Representación esquemática local de puntos pendientes"></svg><p id="map-note">Vista local sin mapas externos.</p>
                <table><thead><tr><th>Fecha</th><th>Hora programada</th><th>Expediente</th><th>Objeto</th><th>Dirección</th><th>Referencia</th><th>Ubicación</th><th>Estado</th><th>Control local</th></tr></thead><tbody>%s</tbody></table>%s</body></html>
                """.formatted(escape(user.getDisplayName()),date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        nowLima().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),visits.size(),rows,guideScript);
        return new HtmlJornadaDownload(html.getBytes(StandardCharsets.UTF_8),"visitas-pendientes-"+date+".html");
    }

    @Transactional
    public void vencerProgramaciones() {
        LocalDate today=LocalDate.now(LIMA);
        var vencidas=programaciones.findAllByOrderByFechaAscHoraAsc().stream()
                .filter(p->p.getEstadoProgramacion()==EstadoProgramacion.PROGRAMADA&&p.getFecha()!=null&&p.getFecha().isBefore(today)
                        &&diligencias.findByProgramacionId(p.getId()).isEmpty()).toList();
        for(var p:vencidas){p.setEstadoProgramacion(EstadoProgramacion.VENCIDA);evento(p.getExpediente(),p,"PROGRAMACION_VENCIDA","Terminó el día programado sin resultado",map("estado","PROGRAMADA"),map("estado","VENCIDA"),"SISTEMA");}
    }

    private boolean validCoordinates(java.math.BigDecimal lat,java.math.BigDecimal lon){return lat!=null&&lon!=null&&lat.compareTo(java.math.BigDecimal.valueOf(-90))>=0&&lat.compareTo(java.math.BigDecimal.valueOf(90))<=0&&lon.compareTo(java.math.BigDecimal.valueOf(-180))>=0&&lon.compareTo(java.math.BigDecimal.valueOf(180))<=0;}
    private String escape(Object value){return Objects.toString(value,"").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    private String horaDoce(LocalTime time){int h=time.getHour();return (h%12==0?12:h%12)+":"+String.format("%02d",time.getMinute())+(h<12?" a. m.":" p. m.");}
    private LocalDateTime nowLima(){return LocalDateTime.now(LIMA);}
    public record HtmlJornadaDownload(byte[] content,String filename){}

    @Transactional
    public void sincronizarSolicitud(Solicitud s,String username){
        if(s.getId()==null)return;
        var maybe=expedientes.findBySolicitudId(s.getId());
        ExpedienteFiscalizacion e=maybe.orElseGet(()->nuevoExpedienteDesdeSolicitud(s,username));
        copiarAdministrado(e,s);
        if(maybe.isEmpty()){
            e.setCodigo(codigoTemporal());e.setFechaCreacion(s.getFechaRegistro()==null?LocalDateTime.now(LIMA):s.getFechaRegistro().atStartOfDay());
            e.setCreadoPor(usuarioNullable(username));e.setOrigen(OrigenFiscalizacion.SOLICITUD);e.setEstado(EstadoExpediente.ABIERTO);
            e.setDetalleOrigen("Registrado desde solicitud heredada "+s.getCodigo());e.setObjetoFiscalizacion("Fiscalización asociada a solicitud "+s.getCodigo());
            expedientes.saveAndFlush(e);e.setCodigo("FIS-"+e.getFechaCreacion().getYear()+"-"+String.format("%05d",e.getId()));expedientes.save(e);
            evento(e,null,"EXPEDIENTE_CREADO","Expediente generado desde solicitud",null,map("origen","SOLICITUD","solicitudCodigo",s.getCodigo()),actor(username));
        }else expedientes.save(e);
        var ps=programaciones.findAllByExpedienteIdOrderByIdAsc(e.getId());
        ProgramacionFiscalizacion actual=ps.isEmpty()?null:ps.getLast();
        EstadoProgramacion resultado=estadoLegacy(s);
        boolean tieneHorario=s.getFechaFiscalizacion()!=null&&s.getHoraFiscalizacion()!=null;
        if(actual==null&&(tieneHorario||resultado==EstadoProgramacion.REALIZADA||resultado==EstadoProgramacion.NO_REALIZADA||resultado==EstadoProgramacion.VENCIDA)){
            actual=crearProgramacionLegacy(e,s,username,resultado);
        }else if(actual!=null&&actual.getEstadoProgramacion()!=EstadoProgramacion.REALIZADA&&actual.getEstadoProgramacion()!=EstadoProgramacion.NO_REALIZADA&&actual.getEstadoProgramacion()!=EstadoProgramacion.CANCELADA&&actual.getEstadoProgramacion()!=EstadoProgramacion.REPROGRAMADA){
            boolean cambio=tieneHorario&&(!Objects.equals(actual.getFecha(),s.getFechaFiscalizacion())||!Objects.equals(actual.getHora(),s.getHoraFiscalizacion())||!Objects.equals(actual.getFiscalizador()==null?null:actual.getFiscalizador().getId(),s.getFiscalizador()==null?null:s.getFiscalizador().getId()));
            if(cambio){
                actual.setEstadoProgramacion(EstadoProgramacion.REPROGRAMADA);var old=actual;
                actual=crearProgramacionLegacy(e,s,username,resultado);actual.setProgramacionAnterior(old);actual.setMotivoReprogramacion("Cambio desde edición de solicitud heredada");
                evento(e,old,"PROGRAMACION_REPROGRAMADA","Programación sustituida desde solicitud heredada",map("estado","PROGRAMADA"),map("estado","REPROGRAMADA","nuevaProgramacionId",actual.getId()),actor(username));
            }else if(resultado==EstadoProgramacion.REALIZADA||resultado==EstadoProgramacion.NO_REALIZADA||resultado==EstadoProgramacion.VENCIDA){
                String anterior=actual.getEstadoProgramacion().name();actual.setEstadoProgramacion(resultado);actual.setResultadoMotivo(s.getObservacionesFiscalizador());
                evento(e,actual,"RESULTADO_REGISTRADO","Resultado sincronizado desde la visita heredada",map("estado",anterior),map("estado",resultado.name()),actor(username));
            }
        }
    }

    @Transactional
    public void validarResultadoRegistrable(Solicitud s){
        if(s.getId()==null){if(s.getEstado()==EstadoSolicitud.EXPIRADO)throw new ConflictException("La visita venció; reprograme antes de registrar resultado.");return;}
        var e=expedientes.findBySolicitudId(s.getId());if(e.isEmpty()){if(s.getEstado()==EstadoSolicitud.EXPIRADO)throw new ConflictException("La visita venció; reprograme antes de registrar resultado.");return;}
        var ps=programaciones.findAllByExpedienteIdOrderByIdAsc(e.get().getId());if(ps.isEmpty()){if(s.getEstado()==EstadoSolicitud.EXPIRADO)throw new ConflictException("La visita venció; reprograme antes de registrar resultado.");return;}
        var estado=ps.getLast().getEstadoProgramacion();
        if(estado==EstadoProgramacion.VENCIDA||estado==EstadoProgramacion.CANCELADA||estado==EstadoProgramacion.REPROGRAMADA)
            throw new ConflictException("La visita ya venció, fue cancelada o reprogramada; revise la programación vigente.");
    }

    @Transactional
    public void sincronizarResultado(Solicitud s,String username){
        if(s.getId()==null)return;var maybe=expedientes.findBySolicitudId(s.getId());if(maybe.isEmpty())return;
        var ps=programaciones.findAllByExpedienteIdOrderByIdAsc(maybe.get().getId());if(ps.isEmpty())return;
        var p=ps.getLast();EstadoProgramacion next=s.getResultadoFiscalizacion()==ResultadoFiscalizacion.REALIZADA?EstadoProgramacion.REALIZADA:
                s.getResultadoFiscalizacion()==ResultadoFiscalizacion.NO_REALIZADA?EstadoProgramacion.NO_REALIZADA:null;
        if(next==null)return;String old=p.getEstadoProgramacion().name();p.setEstadoProgramacion(next);p.setResultadoMotivo(s.getObservacionesFiscalizador());
        evento(maybe.get(),p,"RESULTADO_REGISTRADO","Resultado de visita registrado por el fiscalizador",map("estado",old),map("estado",next.name()),actor(username));
    }

    private ExpedienteFiscalizacion nuevoExpedienteDesdeSolicitud(Solicitud s,String username){var e=new ExpedienteFiscalizacion();e.setSolicitud(s);return e;}
    private void copiarAdministrado(ExpedienteFiscalizacion e,Solicitud s){e.setTipoAdministrado("PERSONA_NATURAL");e.setDocumentoAdministrado(s.getDni());e.setNombreAdministrado((s.getNombres()+" "+s.getApellidos()).trim());e.setTelefonoContacto(s.getTelefono());e.setDireccionFiscalizada(s.getDireccion());}
    private void copiarProgramacionALegacy(ExpedienteFiscalizacion e,ProgramacionFiscalizacion p){if(e.getSolicitud()!=null){var s=e.getSolicitud();s.setFechaFiscalizacion(p.getFecha());s.setHoraFiscalizacion(p.getHora());s.setFiscalizador(p.getFiscalizador());}}
    private EstadoProgramacion estadoLegacy(Solicitud s){
        if(s.getResultadoFiscalizacion()==ResultadoFiscalizacion.REALIZADA)return EstadoProgramacion.REALIZADA;
        if(s.getResultadoFiscalizacion()==ResultadoFiscalizacion.NO_REALIZADA)return EstadoProgramacion.NO_REALIZADA;
        if(s.getEstado()==EstadoSolicitud.EXPIRADO)return EstadoProgramacion.VENCIDA;
        if(s.getFechaFiscalizacion()!=null&&s.getFechaFiscalizacion().isBefore(LocalDate.now(LIMA)))return EstadoProgramacion.VENCIDA;
        return EstadoProgramacion.PROGRAMADA;
    }
    private ProgramacionFiscalizacion crearProgramacionLegacy(ExpedienteFiscalizacion e,Solicitud s,String username,EstadoProgramacion estado){
        var p=new ProgramacionFiscalizacion();p.setExpediente(e);p.setFiscalizador(s.getFiscalizador());p.setFecha(s.getFechaFiscalizacion());p.setHora(s.getHoraFiscalizacion());
        p.setTipoVisita(TipoVisita.PROGRAMADA);p.setEstadoProgramacion(estado);p.setCreadaPor(usuarioNullable(username));
        p.setFechaCreacion(LocalDateTime.now(LIMA));p.setLegacySolicitudCodigo(s.getCodigo());p.setResultadoMotivo(s.getObservacionesFiscalizador());
        p=programaciones.save(p);evento(e,p,"PROGRAMACION_SINCRONIZADA","Programación sincronizada desde Solicitud",null,map("fecha",p.getFecha(),"hora",p.getHora(),"estado",estado.name()),actor(username));return p;
    }
    private String actor(String username){return username==null||username.isBlank()?"SISTEMA":username;}
    private String codigoTemporal(){return "T"+UUID.randomUUID().toString().replace("-","").substring(0,26);}
    private AppUser usuarioNullable(String username){return username==null||username.isBlank()?null:users.findByUsernameIgnoreCaseAndActivoTrue(username).orElse(null);}

    private void validarDisponibilidad(Fiscalizador f,LocalDate fecha,LocalTime hora){
        if(f!=null&&!f.isActivo())throw new ConflictException("El fiscalizador está inactivo.");
        if(f!=null&&programaciones.existsByFiscalizadorIdAndFechaAndHoraAndEstadoProgramacionIn(f.getId(),fecha,hora,List.of(EstadoProgramacion.PROGRAMADA,EstadoProgramacion.EN_CURSO)))
            throw new ConflictException("El fiscalizador ya tiene una visita programada para esa fecha y hora.");
    }
    private void validarFechaFutura(LocalDate fecha,LocalTime hora){if(fecha==null||hora==null||fecha.isBefore(LocalDate.now(LIMA)))throw new ConflictException("La visita debe programarse para hoy o una fecha futura.");}
    private void validarDatosAdministrado(ExpedienteRequest r){
        if(blank(r.direccionFiscalizada())==null)throw new ConflictException("La dirección fiscalizada es obligatoria.");
        if((r.latitud()==null)!=(r.longitud()==null))throw new ConflictException("Indique ambas coordenadas o ninguna.");
        if(r.latitud()!=null&&(r.latitud().compareTo(java.math.BigDecimal.valueOf(-90))<0||r.latitud().compareTo(java.math.BigDecimal.valueOf(90))>0||r.longitud().compareTo(java.math.BigDecimal.valueOf(-180))<0||r.longitud().compareTo(java.math.BigDecimal.valueOf(180))>0))throw new ConflictException("Las coordenadas están fuera de rango.");
        if(blank(r.documentoAdministrado())==null&&blank(r.nombreAdministrado())==null&&blank(r.administradoApellidos())==null)return;
        if(r.documentoAdministrado()==null||!r.documentoAdministrado().matches("\\d{8}"))throw new ConflictException("El DNI debe contener ocho dígitos.");
        if(blank(r.nombreAdministrado())==null||blank(r.administradoApellidos())==null)throw new ConflictException("Complete nombres y apellidos del administrado.");
    }
    private Fiscalizador fiscalizador(String id){return id==null||id.isBlank()?null:fiscalizadores.findById(id).orElseThrow(()->new NotFoundException("Fiscalizador no encontrado."));}
    private AppUser usuario(String username){return users.findByUsernameIgnoreCaseAndActivoTrue(username).orElseThrow(()->new NotFoundException("Usuario autenticado no encontrado."));}
    private ExpedienteFiscalizacion entidad(String codigo){vencerProgramaciones();return expedientes.findByCodigo(codigo).orElseThrow(()->new NotFoundException("Expediente no encontrado: "+codigo));}
    private void evento(ExpedienteFiscalizacion e,ProgramacionFiscalizacion p,String tipo,String descripcion,Map<String,?> antes,Map<String,?> despues,String username){
        var h=new ExpedienteHistorial();h.setExpediente(e);h.setProgramacion(p);h.setFechaHora(LocalDateTime.now(LIMA));h.setActorUsername(username);
        if(!"SISTEMA".equals(username))h.setActor(users.findByUsernameIgnoreCaseAndActivoTrue(username).orElse(null));
        h.setTipoEvento(tipo);h.setDescripcion(descripcion);h.setValoresAnteriores(serialize(antes));h.setValoresNuevos(serialize(despues));historial.save(h);
    }
    private String serialize(Object value){if(value==null)return null;try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private Map<String,Object> map(Object... values){var m=new LinkedHashMap<String,Object>();for(int i=0;i<values.length;i+=2)m.put((String)values[i],values[i+1]);return m;}
    private ExpedienteResponse response(ExpedienteFiscalizacion e){
        var ps=programaciones.findAllByExpedienteIdOrderByIdAsc(e.getId()).stream().map(this::response).toList();
        var hs=historial.findAllByExpedienteIdOrderByFechaHoraAscIdAsc(e.getId()).stream().map(h->new ExpedienteHistorialResponse(h.getFechaHora(),h.getActorUsername(),h.getTipoEvento(),h.getDescripcion(),h.getValoresAnteriores(),h.getValoresNuevos(),h.getProgramacion()==null?null:h.getProgramacion().getId())).toList();
        return new ExpedienteResponse(e.getId(),e.getCodigo(),e.getFechaCreacion(),e.getCreadoPor()==null?null:e.getCreadoPor().getUsername(),e.getOrigen().name(),e.getDetalleOrigen(),e.getDependenciaProcedencia(),e.getReferenciaSolicitud(),e.getEstado().name(),e.getObjetoFiscalizacion(),e.getObservacionesIniciales(),e.getSolicitud()==null?null:e.getSolicitud().getCodigo(),e.getTipoAdministrado(),e.getDocumentoAdministrado(),e.getNombreAdministrado(),e.getTelefonoContacto(),e.getDireccionFiscalizada(),e.getAdministradoApellidos(),e.getReferenciaDomicilio(),e.getLatitud(),e.getLongitud(),e.getDocumentoSustentoNombre(),e.getDocumentoSustentoFecha(),e.getSolicitud()==null?null:e.getSolicitud().getReporteNombreOriginal(),ps,hs);
    }
    private ProgramacionFiscalizacionResponse response(ProgramacionFiscalizacion p){return new ProgramacionFiscalizacionResponse(p.getId(),p.getFecha(),p.getHora(),p.getTipoVisita().name(),p.getEstadoProgramacion().name(),p.getFiscalizador()==null?null:p.getFiscalizador().getId(),p.getFiscalizador()==null?null:p.getFiscalizador().getNombre(),p.getProgramacionAnterior()==null?null:p.getProgramacionAnterior().getId(),p.getMotivoReprogramacion(),p.getMotivoCancelacion(),p.getMotivoSeguimiento(),p.getRevisionOrigen()==null?null:p.getRevisionOrigen().getId());}
    private void asegurarNoCerrado(ExpedienteFiscalizacion e){if(e.getEstado()==EstadoExpediente.CERRADO||e.getEstado()==EstadoExpediente.CANCELADO)throw new ConflictException("El expediente está cerrado o cancelado y no admite operaciones.");}
    private String blank(String s){return s==null||s.isBlank()?null:s.trim();}
}

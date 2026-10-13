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
    private final MunicipalAccessService access;

    public ExpedienteFiscalizacionService(ExpedienteFiscalizacionRepository expedientes,
            ProgramacionFiscalizacionRepository programaciones, DiligenciaFiscalizacionRepository diligencias,
            ExpedienteHistorialRepository historial,
            AppUserRepository users, FiscalizadorRepository fiscalizadores, SolicitudRepository solicitudes,
            ObjectMapper json, DocumentoStorageService documentos,MunicipalAccessService access) {
        this.expedientes=expedientes; this.programaciones=programaciones; this.diligencias=diligencias; this.historial=historial;
        this.users=users; this.fiscalizadores=fiscalizadores; this.solicitudes=solicitudes; this.json=json; this.documentos=documentos;this.access=access;
    }

    @Transactional
    public List<ExpedienteResponse> listar() {
        var actor=access.current();return expedientes.findAllByOrderByFechaCreacionDescIdDesc().stream().filter(e->access.canRead(e,actor)).map(this::response).toList();
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
        p.setFechaCreacion(LocalDateTime.now(LIMA));p.setCreadaPor(actor);p.setFiscalizador(fiscalizador(r.fiscalizadorId()));access.requireAssignment(actor,p.getFiscalizador());
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
        access.requireAssignment(usuario(username),nueva.getFiscalizador());validarDisponibilidad(nueva.getFiscalizador(),nueva.getFecha(),nueva.getHora());programaciones.save(nueva);
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
        StringBuilder cards=new StringBuilder();
        for(int i=0;i<visits.size();i++){
            var p=visits.get(i);var e=p.getExpediente();String address=Objects.toString(e.getDireccionFiscalizada(),"").trim();
            String destination=validCoordinates(e.getLatitud(),e.getLongitud())?e.getLatitud()+","+e.getLongitud():address;
            String administrado=String.join(" ",java.util.stream.Stream.of(e.getNombreAdministrado(),e.getAdministradoApellidos()).filter(v->v!=null&&!v.isBlank()).toList());
            String status=switch(p.getEstadoProgramacion()){
                case EN_CURSO -> "En curso";case VENCIDA -> "Vencida";default -> "Pendiente";
            };
            cards.append("<article class=\"visit-card\" data-destination=\"").append(escape(destination)).append("\"><div class=\"visit-top\"><span class=\"visit-number\">")
                    .append(i+1).append("</span><span class=\"status status-").append(p.getEstadoProgramacion().name().toLowerCase(Locale.ROOT))
                    .append("\">").append(escape(status)).append("</span></div><p class=\"time\">")
                    .append(escape(p.getHora().format(DateTimeFormatter.ofPattern("HH:mm")))).append(" · ").append(escape(horaDoce(p.getHora())))
                    .append("</p><h2>").append(escape(e.getCodigo())).append("</h2><p class=\"administrado\">")
                    .append(escape(administrado.isBlank()?"Administrado sin identificar":administrado)).append("</p><p class=\"object\">")
                    .append(escape(e.getObjetoFiscalizacion())).append("</p><dl><dt>Dirección</dt><dd>")
                    .append(escape(address.isBlank()?"Sin dirección":address)).append("</dd><dt>Referencia</dt><dd>")
                    .append(escape(Objects.toString(e.getReferenciaDomicilio(),"Sin referencia")))
                    .append("</dd></dl><div class=\"visit-actions\"><button type=\"button\" data-toggle>Marcar atendida (solo esta guía)</button></div>")
                    .append("<label class=\"hour-label\">Hora de atención opcional <input type=\"time\" data-hour></label><p data-local-state class=\"local-state\" aria-live=\"polite\"></p></article>");
        }
        if(visits.isEmpty())cards.append("<div class=\"empty\">No hay visitas asignadas para esta fecha.</div>");
        String html="""
                <!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Guía de jornada · __FECHA__</title><style>
                :root{color-scheme:light;--green:#155c49;--green2:#19745c;--gold:#caa75e;--ink:#1b352a;--muted:#62756a;--line:#d9e8dd;--paper:#f3f7f3}
                *{box-sizing:border-box}body{margin:0;background:linear-gradient(180deg,#eaf3ec 0,#f5f8f5 330px,#f7f9f7 100%);color:var(--ink);font:16px/1.55 system-ui,-apple-system,"Segoe UI",sans-serif}
                button,input{font:inherit}button:focus-visible,input:focus-visible{outline:3px solid #91c9a5;outline-offset:2px}
                .hero{position:relative;overflow:hidden;background:linear-gradient(110deg,#103c32,#16674e 70%,#277653);color:white;padding:35px max(20px,calc((100vw - 1080px)/2)) 39px;border-bottom:4px solid var(--gold)}
                .hero:after{content:"";position:absolute;right:4%;top:-95px;width:275px;height:275px;border:1px solid #ffffff26;border-radius:50%;box-shadow:0 0 0 55px #ffffff09;pointer-events:none}
                .eyebrow{text-transform:uppercase;letter-spacing:.13em;font-size:.72rem;font-weight:800;color:#d8e6db;margin:0 0 8px}.hero h1{position:relative;margin:0;font-size:clamp(1.9rem,4vw,2.75rem);letter-spacing:-.035em;line-height:1.12}.hero-meta{position:relative;display:flex;gap:10px;flex-wrap:wrap;margin-top:18px;color:#ecf5ed;font-size:.86rem}.hero-meta span{padding:7px 11px;border:1px solid #ffffff50;border-radius:8px;background:#ffffff14}
                main{max-width:1112px;margin:25px auto;padding:0 16px}.metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.metric,.visit-card,.route-panel{background:#fff;border:1px solid var(--line);border-radius:15px;box-shadow:0 6px 20px #164d3b10}.metric{position:relative;min-height:101px;display:flex;flex-direction:column;justify-content:center;padding:17px 20px;border-top:3px solid #62a87b}.metric:nth-child(2){border-top-color:#caa75e}.metric:nth-child(3){border-top-color:#8cb99c}.metric span{display:block;color:var(--muted);font-size:.82rem;font-weight:650}.metric strong{display:block;color:var(--green);font-size:1.9rem;line-height:1.1;letter-spacing:-.035em}
                .route-panel{display:flex;align-items:center;gap:12px 18px;flex-wrap:wrap;padding:18px 20px;margin:18px 0 29px;border-color:#b9d8c3;background:linear-gradient(110deg,#f2faf4,#fff 72%)}.route-button,.visit-actions button{display:inline-flex;align-items:center;justify-content:center;min-height:44px;padding:9px 16px;border:1px solid var(--green);border-radius:9px;background:var(--green);color:white;font-size:.88rem;font-weight:750;line-height:1.25;cursor:pointer;box-shadow:0 3px 8px #155c4925}.route-button:hover:not(:disabled){background:#0f4e3c}.route-button:disabled{opacity:.55;cursor:not-allowed;box-shadow:none}.route-note{flex:1;min-width:210px;color:#4d6656;font-size:.83rem;line-height:1.45}
                .visits-heading{margin:0 0 13px;padding-left:12px;border-left:4px solid #2b8658;color:#194c35;font-size:1.23rem;letter-spacing:-.02em}.visits{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.visit-card{min-width:0;display:flex;flex-direction:column;padding:19px 20px;border-color:#dbe8de;transition:opacity .2s,border-color .2s}.visit-card[data-attended=yes]{opacity:.72;border-color:#8db7a7}.visit-top{display:flex;justify-content:space-between;align-items:center;gap:10px}.visit-number{display:grid;place-items:center;width:34px;height:34px;flex:none;border-radius:50%;background:var(--green);color:white;font-size:.9rem;font-weight:800}.status{border:1px solid #f0ddab;border-radius:999px;padding:5px 10px;background:#fff6df;color:#76540b;font-size:.74rem;font-weight:800}.status-en_curso{border-color:#bae1c9;background:#e2f3e9;color:#155c49}.status-vencida{border-color:#f1c9c4;background:#fff0ed;color:#9b2c20}.time{align-self:start;margin:14px 0 7px;padding:5px 9px;border-radius:7px;background:#e9f5ec;color:#1b6d4b;font-size:.86rem;font-weight:800}.visit-card h2{margin:0 0 4px;color:#123f2e;font-size:1.2rem;letter-spacing:-.025em}.administrado{margin:0 0 5px;color:#263f30;font-weight:750;font-size:.95rem}.object{margin:0 0 13px;color:#617369;font-size:.87rem;line-height:1.45}.visit-card dl{display:grid;grid-template-columns:86px minmax(0,1fr);gap:8px 10px;margin:0;padding:12px;border:1px solid #e6eee8;border-radius:9px;background:#f8fbf8;font-size:.84rem}.visit-card dt{color:#60796a;font-size:.77rem;font-weight:750}.visit-card dd{margin:0;overflow-wrap:anywhere}.visit-actions{margin-top:auto;padding-top:15px}.visit-actions button{min-height:38px;background:#f2f9f3;border-color:#b8d6c1;color:var(--green);box-shadow:none}.visit-actions button:hover{background:#e3f1e6}.hour-label{display:flex;gap:8px;align-items:center;margin-top:12px;color:var(--muted);font-size:.8rem;flex-wrap:wrap}.hour-label input{min-height:37px;border:1px solid #b7cfc0;border-radius:7px;padding:6px 9px;background:#fff;color:var(--ink)}.local-state{min-height:1.2em;margin:8px 0 0;color:var(--green);font-weight:700;font-size:.83rem}.empty{grid-column:1/-1;padding:30px;border:1px dashed #bcd4c3;border-radius:13px;background:white;text-align:center;color:var(--muted)}footer{max-width:1080px;margin:22px auto 30px;padding:0 16px;color:var(--muted);font-size:.78rem;line-height:1.5}
                @media(max-width:680px){.metrics{grid-template-columns:1fr 1fr;gap:9px}.metric{min-height:84px;padding:13px}.metric strong{font-size:1.5rem}.metrics .metric:first-child{grid-column:1/-1}.visits{grid-template-columns:1fr}.hero{padding:26px 16px 29px}main{margin-top:17px;padding:0 12px}.route-panel{padding:15px}.route-button{width:100%}.route-note{min-width:0}.visit-card{padding:16px}}
                @media print{body{background:#fff}.hero{print-color-adjust:exact}.visit-card,.metric,.route-panel{box-shadow:none;break-inside:avoid}.visit-actions button,.route-button{display:none}}
                </style></head><body><header class="hero"><p class="eyebrow">Municipalidad Distrital de San Miguel · Fiscalización</p><h1>Guía de jornada</h1><div class="hero-meta"><span><strong>Fiscalizador:</strong> __NOMBRE__</span><span><strong>Fecha:</strong> __FECHA__</span></div></header>
                <main><section class="metrics" aria-label="Resumen local"><article class="metric"><strong>__TOTAL__</strong><span>Visitas asignadas</span></article><article class="metric"><strong id="pending-count">__TOTAL__</strong><span>Pendientes en esta guía</span></article><article class="metric"><strong id="attended-count">0</strong><span>Atendidas localmente</span></article></section>
                <section class="route-panel"><button id="route-button" class="route-button" type="button">Consultar recorrido pendiente</button><span id="route-note" class="route-note" role="status"></span></section>
                <h2 class="visits-heading">Visitas asignadas</h2><section class="visits">__CARDS__</section></main>
                <footer>Guía generada el __GENERADO__ (America/Lima). Las marcas de atención solo cambian este archivo durante la sesión y nunca actualizan el sistema.</footer>
                <script>
                (()=>{
                  const cards=[...document.querySelectorAll('.visit-card')],route=document.getElementById('route-button'),note=document.getElementById('route-note'),pending=document.getElementById('pending-count'),attended=document.getElementById('attended-count');
                  function destinations(){return cards.filter(c=>c.dataset.attended!=='yes').map(c=>c.dataset.destination.trim()).filter(Boolean);}
                  function googleUrl(stops){const url=new URL(stops.length===1?'https://www.google.com/maps/search/':'https://www.google.com/maps/dir/');url.searchParams.set('api','1');if(stops.length===1)url.searchParams.set('query',stops[0]);else{url.searchParams.set('origin',stops[0]);url.searchParams.set('destination',stops[stops.length-1]);if(stops.length>2)url.searchParams.set('waypoints',stops.slice(1,-1).join('|'));}return url.href;}
                  function render(){const active=cards.filter(c=>c.dataset.attended!=='yes'),stops=destinations();pending.textContent=String(active.length);attended.textContent=String(cards.length-active.length);route.disabled=!stops.length;note.textContent=!active.length?'No quedan visitas pendientes.':!stops.length?'Las visitas pendientes no tienen dirección ni coordenadas para construir el recorrido.':stops.length<active.length?'Algunas visitas no tienen ubicación suficiente y se omiten del recorrido.':'Se abrirá Google Maps con las visitas pendientes.';}
                  route.addEventListener('click',()=>{const stops=destinations();if(stops.length)window.open(googleUrl(stops),'_blank','noopener,noreferrer');});
                  cards.forEach(card=>{const button=card.querySelector('[data-toggle]'),hour=card.querySelector('[data-hour]'),state=card.querySelector('[data-local-state]');button.addEventListener('click',()=>{const done=card.dataset.attended!=='yes';card.dataset.attended=done?'yes':'no';button.textContent=done?'Desmarcar atención local':'Marcar atendida (solo esta guía)';state.textContent=done?'Atendida en esta guía'+(hour.value?' a las '+hour.value:''):'Marca local retirada.';render();});hour.addEventListener('change',()=>{if(card.dataset.attended==='yes')state.textContent='Atendida en esta guía'+(hour.value?' a las '+hour.value:'');});});render();
                })();
                </script></body></html>
                """.replace("__NOMBRE__",escape(user.getDisplayName())).replace("__FECHA__",date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .replace("__TOTAL__",Integer.toString(visits.size())).replace("__CARDS__",cards.toString())
                .replace("__GENERADO__",nowLima().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
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
        if(f!=null)fiscalizadores.findForIdentityLink(f.getId()); // Serialize assignment checks for the same inspector.

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

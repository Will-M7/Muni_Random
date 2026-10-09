package pe.gob.munisanmiguel.controller;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.gob.munisanmiguel.dto.ApiDtos.*;
import pe.gob.munisanmiguel.service.SolicitudService;
import pe.gob.munisanmiguel.service.ExpedienteFiscalizacionService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import pe.gob.munisanmiguel.entity.ResultadoFiscalizacion;
import java.util.List;

@RestController @RequestMapping("/api/fiscalizador")
public class FiscalizadorController {
    private final SolicitudService service;
    private final ExpedienteFiscalizacionService expedientes;
    public FiscalizadorController(SolicitudService service, ExpedienteFiscalizacionService expedientes) { this.service = service; this.expedientes=expedientes; }
    @GetMapping("/mis-tareas") @PreAuthorize("hasAuthority('TAREAS_PROPIAS_VER')") public List<FiscalizadorTaskResponse> tareas(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fecha, Authentication auth) { service.expirarPendientes(); return service.misTareas(auth, fecha); }
    @GetMapping("/mis-tareas/proximas") @PreAuthorize("hasAuthority('TAREAS_PROPIAS_VER')") public List<FiscalizadorTaskResponse> proximas(Authentication auth) { service.expirarPendientes(); return service.misProximas(auth); }
    @GetMapping("/mis-expedientes") @PreAuthorize("hasAuthority('TAREAS_PROPIAS_VER')") public List<FiscalizadorExpedienteTaskResponse> misExpedientes(Authentication auth) { service.expirarPendientes(); return expedientes.misTareasCampo(auth.getName()); }
    @GetMapping(value="/mis-visitas.html",produces=MediaType.TEXT_HTML_VALUE) @PreAuthorize("hasAuthority('TAREAS_PROPIAS_VER')")
    public ResponseEntity<byte[]> jornada(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fecha,Authentication auth){
        var d=expedientes.jornadaHtml(auth.getName(),fecha);
        return ResponseEntity.ok().contentType(new MediaType("text","html",StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(d.filename(),StandardCharsets.UTF_8).build().toString())
                .body(d.content());
    }
    @GetMapping("/mis-tareas/historial") @PreAuthorize("hasAuthority('TAREAS_PROPIAS_VER')") public List<FiscalizadorTaskResponse> historial(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fecha, @RequestParam(required=false) ResultadoFiscalizacion resultado, Authentication auth) { service.expirarPendientes(); return service.miHistorial(auth, fecha, resultado); }
    @GetMapping("/ruta") @PreAuthorize("hasAuthority('RUTA_VER')") public List<FiscalizadorTaskResponse> ruta(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fecha, Authentication auth) { service.expirarPendientes(); return service.misTareas(auth, fecha); }
    @GetMapping("/mis-tareas/{codigo}") @PreAuthorize("hasAuthority('TAREAS_PROPIAS_VER')") public SolicitudResponse detalle(@PathVariable String codigo, Authentication auth) { service.expirarPendientes(); return service.mapperResponse(service.tareaPropia(codigo, auth)); }
    @PutMapping(value="/mis-tareas/{codigo}/resultado", consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAuthority('FISCALIZACION_EJECUTAR') and hasAuthority('RESULTADO_REGISTRAR')") public SolicitudResponse resultado(@PathVariable String codigo, @Valid @RequestPart("resultado") ResultadoFiscalizacionRequest request, @RequestPart(value="reporte", required=false) MultipartFile reporte, Authentication auth) { service.expirarPendientes(); return service.registrarResultado(codigo, request, reporte, auth); }
    @GetMapping("/mis-tareas/{codigo}/documento") @PreAuthorize("hasAuthority('DOCUMENTO_TAREA_VER')") public ResponseEntity<Resource> documento(@PathVariable String codigo, Authentication auth) { service.tareaPropia(codigo, auth); var d=service.documento(codigo); return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(d.nombre(), StandardCharsets.UTF_8).build().toString()).body(d.recurso()); }
    @GetMapping("/mis-tareas/{codigo}/reporte") @PreAuthorize("hasAuthority('TAREAS_PROPIAS_VER')") public ResponseEntity<Resource> reporte(@PathVariable String codigo, Authentication auth) { service.tareaPropia(codigo, auth); var d=service.reporte(codigo); return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(d.nombre(), StandardCharsets.UTF_8).build().toString()).body(d.recurso()); }
}

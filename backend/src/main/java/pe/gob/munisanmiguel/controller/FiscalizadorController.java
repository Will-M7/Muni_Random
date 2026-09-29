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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import pe.gob.munisanmiguel.entity.ResultadoFiscalizacion;
import java.util.List;

@RestController @RequestMapping("/api/fiscalizador") @PreAuthorize("hasRole('FISCALIZADOR')")
public class FiscalizadorController {
    private final SolicitudService service;
    public FiscalizadorController(SolicitudService service) { this.service = service; }
    @GetMapping("/mis-tareas") public List<FiscalizadorTaskResponse> tareas(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fecha, Authentication auth) { service.expirarPendientes(); return service.misTareas(auth, fecha); }
    @GetMapping("/mis-tareas/proximas") public List<FiscalizadorTaskResponse> proximas(Authentication auth) { service.expirarPendientes(); return service.misProximas(auth); }
    @GetMapping("/mis-tareas/historial") public List<FiscalizadorTaskResponse> historial(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fecha, @RequestParam(required=false) ResultadoFiscalizacion resultado, Authentication auth) { service.expirarPendientes(); return service.miHistorial(auth, fecha, resultado); }
    @GetMapping("/ruta") public List<FiscalizadorTaskResponse> ruta(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fecha, Authentication auth) { service.expirarPendientes(); return service.misTareas(auth, fecha); }
    @GetMapping("/mis-tareas/{codigo}") public SolicitudResponse detalle(@PathVariable String codigo, Authentication auth) { service.expirarPendientes(); return service.mapperResponse(service.tareaPropia(codigo, auth)); }
    @PutMapping(value="/mis-tareas/{codigo}/resultado", consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public SolicitudResponse resultado(@PathVariable String codigo, @Valid @RequestPart("resultado") ResultadoFiscalizacionRequest request, @RequestPart(value="reporte", required=false) MultipartFile reporte, Authentication auth) { service.expirarPendientes(); return service.registrarResultado(codigo, request, reporte, auth); }
    @GetMapping("/mis-tareas/{codigo}/documento") public ResponseEntity<Resource> documento(@PathVariable String codigo, Authentication auth) { service.tareaPropia(codigo, auth); var d=service.documento(codigo); return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(d.nombre(), StandardCharsets.UTF_8).build().toString()).body(d.recurso()); }
    @GetMapping("/mis-tareas/{codigo}/reporte") public ResponseEntity<Resource> reporte(@PathVariable String codigo, Authentication auth) { service.tareaPropia(codigo, auth); var d=service.reporte(codigo); return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(d.nombre(), StandardCharsets.UTF_8).build().toString()).body(d.recurso()); }
}

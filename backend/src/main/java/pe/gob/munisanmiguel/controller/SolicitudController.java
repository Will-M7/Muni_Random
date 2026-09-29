package pe.gob.munisanmiguel.controller;

import pe.gob.munisanmiguel.dto.ApiDtos.*; import pe.gob.munisanmiguel.service.SolicitudService; import jakarta.validation.Valid; import org.springframework.format.annotation.DateTimeFormat; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import org.springframework.http.*; import org.springframework.core.io.Resource; import java.nio.charset.StandardCharsets; import java.time.LocalDate; import java.util.List;
@RestController @RequestMapping("/api/solicitudes") public class SolicitudController {
    private final SolicitudService service; public SolicitudController(SolicitudService s){service=s;}
    @GetMapping public List<SolicitudResponse> listar(@RequestParam(required=false) String q,@RequestParam(required=false) String estado,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fecha,@RequestParam(required=false) String fiscalizadorId){service.expirarPendientes();return service.buscar(q,estado,fecha,fiscalizadorId);}
    @GetMapping("/programacion") public List<ProgramacionResponse> programacion(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fecha){service.expirarPendientes();return service.programacion(fecha);}
    @GetMapping("/{codigo}") public SolicitudResponse obtener(@PathVariable String codigo){service.expirarPendientes();return service.porCodigo(codigo);}
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasRole('MUNICIPIO')") @ResponseStatus(HttpStatus.CREATED) public SolicitudResponse crear(@Valid @RequestPart("solicitud") SolicitudRequest r, @RequestPart("documento") org.springframework.web.multipart.MultipartFile file){return service.crear(r,file);}
    @PutMapping(value="/{codigo}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasRole('MUNICIPIO')") public SolicitudResponse actualizar(@PathVariable String codigo,@Valid @RequestPart("solicitud") SolicitudRequest r,@RequestPart(value="documento",required=false) org.springframework.web.multipart.MultipartFile file){return service.actualizar(codigo,r,file);}
    @PatchMapping("/{codigo}/estado") @PreAuthorize("hasAnyRole('MUNICIPIO','FISCALIZADOR')") public SolicitudResponse cambiarEstado(@PathVariable String codigo,@Valid @RequestBody EstadoRequest r){return service.cambiarEstado(codigo,r);}
    @DeleteMapping("/{codigo}") @PreAuthorize("hasRole('MUNICIPIO')") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void eliminar(@PathVariable String codigo){service.eliminar(codigo);}
    @GetMapping("/{codigo}/documento") public ResponseEntity<Resource> documento(@PathVariable String codigo) {
        var solicitud = service.documento(codigo);
        String disposition = ContentDisposition.inline().filename(solicitud.nombre(), StandardCharsets.UTF_8).build().toString();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION, disposition).body(solicitud.recurso());
    }
    @GetMapping("/{codigo}/reporte") @PreAuthorize("hasRole('MUNICIPIO')") public ResponseEntity<Resource> reporte(@PathVariable String codigo) { var reporte=service.reporte(codigo); String disposition=ContentDisposition.inline().filename(reporte.nombre(),StandardCharsets.UTF_8).build().toString(); return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,disposition).body(reporte.recurso()); }
}

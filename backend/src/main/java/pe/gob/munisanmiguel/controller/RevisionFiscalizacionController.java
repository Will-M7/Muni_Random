package pe.gob.munisanmiguel.controller;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.gob.munisanmiguel.dto.ApiDtos.*;
import pe.gob.munisanmiguel.service.RevisionFiscalizacionService;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController @RequestMapping("/api/revisiones")
public class RevisionFiscalizacionController {
    private final RevisionFiscalizacionService service;
    public RevisionFiscalizacionController(RevisionFiscalizacionService service){this.service=service;}

    @GetMapping("/pendientes") @PreAuthorize("hasAuthority('REVISION_VER')")
    public List<Map<String,Object>> pendientes(){return service.pendientes();}
    @PostMapping("/diligencias/{id}/iniciar") @PreAuthorize("hasAuthority('REVISION_INICIAR')")
    public Map<String,Object> iniciar(@PathVariable Long id,Authentication auth){return service.iniciar(id,auth.getName());}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('REVISION_VER')")
    public Map<String,Object> obtener(@PathVariable Long id){return service.obtener(id);}
    @GetMapping("/expediente/{codigo}") @PreAuthorize("hasAuthority('REVISION_VER')")
    public Map<String,Object> expediente(@PathVariable String codigo){return service.expediente(codigo);}
    @PostMapping("/{id}/aclaraciones") @PreAuthorize("hasAuthority('ACLARACION_SOLICITAR')")
    public Map<String,Object> solicitar(@PathVariable Long id,@Valid @RequestBody SolicitarAclaracionRequest req,Authentication auth){return service.solicitar(id,req,auth.getName());}
    @GetMapping("/mis-aclaraciones") @PreAuthorize("hasAuthority('ACLARACION_RESPONDER')")
    public List<Map<String,Object>> aclaraciones(Authentication auth){return service.misAclaraciones(auth.getName());}
    @PostMapping("/aclaraciones/{id}/responder") @PreAuthorize("hasAuthority('ACLARACION_RESPONDER')")
    public Map<String,Object> responder(@PathVariable Long id,@Valid @RequestBody ResponderAclaracionRequest req,Authentication auth){return service.responder(id,req,auth.getName());}
    @PostMapping("/{id}/concluir") @PreAuthorize("hasAuthority('REVISION_CONCLUIR') and hasAuthority('EXPEDIENTE_CERRAR')")
    public Map<String,Object> concluir(@PathVariable Long id,@Valid @RequestBody ConcluirRevisionRequest req,Authentication auth){return service.concluir(id,req,auth.getName());}
    @PostMapping("/{id}/seguimientos") @PreAuthorize("hasAuthority('SEGUIMIENTO_CREAR')")
    public Map<String,Object> seguimiento(@PathVariable Long id,@Valid @RequestBody SeguimientoRequest req,Authentication auth){return service.seguimiento(id,req,auth.getName());}
    @GetMapping("/{id}/informe.pdf") @PreAuthorize("hasAuthority('INFORME_CONCLUSION_VER')")
    public ResponseEntity<Resource> informe(@PathVariable Long id){var f=service.informe(id);return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(f.filename(),StandardCharsets.UTF_8).build().toString()).body(f.resource());}
    @GetMapping("/diligencias/{id}/evidencias/{evidenciaId}") @PreAuthorize("hasAuthority('REVISION_VER')")
    public ResponseEntity<Resource> evidencia(@PathVariable Long id,@PathVariable Long evidenciaId){var f=service.evidencia(id,evidenciaId);return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.mime())).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(f.filename(),StandardCharsets.UTF_8).build().toString()).body(f.resource());}
}

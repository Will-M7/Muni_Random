package pe.gob.munisanmiguel.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.gob.munisanmiguel.dto.ApiDtos.ActaResponse;
import pe.gob.munisanmiguel.service.DiligenciaFiscalizacionService;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController @RequestMapping("/api/actas")
public class ActaFiscalizacionController {
    private final DiligenciaFiscalizacionService service;
    public ActaFiscalizacionController(DiligenciaFiscalizacionService service){this.service=service;}

    @GetMapping("/expediente/{codigo}") @PreAuthorize("hasAuthority('ACTA_VER')")
    public List<ActaResponse> expediente(@PathVariable String codigo,Authentication auth){return service.actasExpediente(codigo,auth.getName(),esFiscalizador(auth));}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('ACTA_VER')")
    public ActaResponse acta(@PathVariable Long id,Authentication auth){return service.acta(id,auth.getName(),esFiscalizador(auth));}
    @GetMapping("/{id}/pdf") @PreAuthorize("hasAuthority('ACTA_VER')")
    public ResponseEntity<Resource> pdf(@PathVariable Long id,Authentication auth){return file(service.pdfActa(id,auth.getName(),false,esFiscalizador(auth)));}
    @GetMapping("/{id}/copia-firmada") @PreAuthorize("hasAuthority('ACTA_VER')")
    public ResponseEntity<Resource> copiaFirmada(@PathVariable Long id,Authentication auth){return file(service.pdfActa(id,auth.getName(),true,esFiscalizador(auth)));}
    @GetMapping(value="/{id}/evidencias/{evidenceId}") @PreAuthorize("hasAuthority('ACTA_VER')")
    public ResponseEntity<Resource> evidencia(@PathVariable Long id,@PathVariable Long evidenceId,Authentication auth){return file(service.evidenciaActa(id,evidenceId,auth.getName(),esFiscalizador(auth)));}
    @PostMapping(value="/{id}/copia-firmada",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ACTA_FIRMADA_SUBIR')")
    public ActaResponse subirCopia(@PathVariable Long id,@RequestPart("archivo") MultipartFile archivo,Authentication auth){return service.subirCopiaFirmada(id,archivo,auth.getName());}

    private boolean esFiscalizador(Authentication auth){return auth.getAuthorities().stream().anyMatch(a->"TAREAS_PROPIAS_VER".equals(a.getAuthority()));}
    private ResponseEntity<Resource> file(DiligenciaFiscalizacionService.FileDownload f){return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType())).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(f.filename(),StandardCharsets.UTF_8).build().toString()).body(f.resource());}
}

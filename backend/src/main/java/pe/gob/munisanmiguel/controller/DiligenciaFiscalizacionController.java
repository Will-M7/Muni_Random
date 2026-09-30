package pe.gob.munisanmiguel.controller;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.gob.munisanmiguel.dto.ApiDtos.*;
import pe.gob.munisanmiguel.entity.TipoEvidencia;
import pe.gob.munisanmiguel.service.DiligenciaFiscalizacionService;
import java.nio.charset.StandardCharsets;

@RestController @RequestMapping("/api/fiscalizador")
public class DiligenciaFiscalizacionController {
    private final DiligenciaFiscalizacionService service;
    public DiligenciaFiscalizacionController(DiligenciaFiscalizacionService service){this.service=service;}

    @GetMapping("/programaciones/{programacionId}/diligencia")
    @PreAuthorize("hasAuthority('TAREAS_PROPIAS_VER')")
    public DiligenciaWorkspaceResponse espacio(@PathVariable Long programacionId,Authentication auth){return service.espacio(auth.getName(),programacionId);}

    @PostMapping("/programaciones/{programacionId}/diligencia/iniciar")
    @PreAuthorize("hasAuthority('DILIGENCIA_INICIAR')")
    public DiligenciaWorkspaceResponse iniciar(@PathVariable Long programacionId,@RequestBody(required=false) InicioDiligenciaRequest request,Authentication auth){return service.iniciar(programacionId,request,auth.getName());}

    @PostMapping("/programaciones/{programacionId}/diligencia/no-realizada")
    @PreAuthorize("hasAuthority('DILIGENCIA_FINALIZAR')")
    public DiligenciaWorkspaceResponse noRealizada(@PathVariable Long programacionId,@Valid @RequestBody NoRealizadaRequest request,Authentication auth){return service.noRealizada(programacionId,request,auth.getName());}

    @PatchMapping("/diligencias/{id}")
    @PreAuthorize("hasAuthority('DILIGENCIA_EDITAR_PROPIA')")
    public DiligenciaWorkspaceResponse guardar(@PathVariable Long id,@RequestBody GuardarDiligenciaRequest request,Authentication auth){return service.guardar(id,request,auth.getName());}

    @PostMapping("/diligencias/{id}/participantes")
    @PreAuthorize("hasAuthority('DILIGENCIA_EDITAR_PROPIA')")
    public ParticipanteResponse participante(@PathVariable Long id,@Valid @RequestBody ParticipanteRequest request,Authentication auth){return service.agregarParticipante(id,request,auth.getName());}

    @PostMapping(value="/diligencias/{id}/evidencias",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('EVIDENCIA_AGREGAR')")
    public EvidenciaResponse evidencia(@PathVariable Long id,@RequestParam TipoEvidencia tipo,@RequestParam(required=false) String descripcion,@RequestPart("archivo") MultipartFile archivo,Authentication auth){return service.agregarEvidencia(id,tipo,descripcion,archivo,auth.getName());}

    @GetMapping("/diligencias/{id}/evidencias/{evidenciaId}/archivo")
    @PreAuthorize("hasAuthority('EVIDENCIA_AGREGAR')")
    public ResponseEntity<Resource> archivo(@PathVariable Long id,@PathVariable Long evidenciaId,Authentication auth){return response(service.evidencia(id,evidenciaId,auth.getName(),true));}

    @GetMapping("/programaciones/{programacionId}/documento-sustento")
    @PreAuthorize("hasAuthority('DOCUMENTO_TAREA_VER')")
    public ResponseEntity<Resource> sustento(@PathVariable Long programacionId,Authentication auth){return response(service.sustento(programacionId,auth.getName()));}

    @PostMapping("/diligencias/{id}/finalizar")
    @PreAuthorize("hasAuthority('DILIGENCIA_FINALIZAR') and hasAuthority('ACTA_GENERAR')")
    public ActaResponse finalizar(@PathVariable Long id,@Valid @RequestBody FinalizarDiligenciaRequest request,Authentication auth){return service.finalizar(id,request,auth.getName());}

    private ResponseEntity<Resource> response(DiligenciaFiscalizacionService.FileDownload f){return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType())).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(f.filename(),StandardCharsets.UTF_8).build().toString()).body(f.resource());}
}

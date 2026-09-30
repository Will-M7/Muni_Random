package pe.gob.munisanmiguel.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.multipart.MultipartFile;
import pe.gob.munisanmiguel.dto.ApiDtos.*;
import pe.gob.munisanmiguel.service.ExpedienteFiscalizacionService;
import java.util.List;

@RestController @RequestMapping("/api/expedientes")
public class ExpedienteFiscalizacionController {
    private final ExpedienteFiscalizacionService service;
    public ExpedienteFiscalizacionController(ExpedienteFiscalizacionService service){this.service=service;}

    @GetMapping @PreAuthorize("hasAuthority('EXPEDIENTE_VER')")
    public List<ExpedienteResponse> listar(){service.vencerProgramaciones();return service.listar();}
    @GetMapping("/{codigo}") @PreAuthorize("hasAuthority('EXPEDIENTE_VER')")
    public ExpedienteResponse obtener(@PathVariable String codigo){return service.obtener(codigo);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('EXPEDIENTE_CREAR')")
    public ExpedienteResponse crear(@Valid @RequestBody ExpedienteRequest request,Authentication auth){return service.crear(request,auth.getName());}
    @PostMapping(value="/{codigo}/documento",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('EXPEDIENTE_EDITAR')")
    public ExpedienteResponse adjuntarDocumento(@PathVariable String codigo,@RequestPart("documento") MultipartFile documento,Authentication auth){return service.adjuntarDocumento(codigo,documento,auth.getName());}
    @GetMapping("/{codigo}/documento") @PreAuthorize("hasAuthority('EXPEDIENTE_VER')")
    public ResponseEntity<Resource> documento(@PathVariable String codigo){Resource r=service.documento(codigo);if(r==null)return ResponseEntity.notFound().build();return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\"sustento.pdf\"").body(r);}
    @PutMapping("/{codigo}") @PreAuthorize("hasAuthority('EXPEDIENTE_EDITAR')")
    public ExpedienteResponse editar(@PathVariable String codigo,@Valid @RequestBody ExpedienteRequest request,Authentication auth){return service.editar(codigo,request,auth.getName());}
    @PostMapping("/{codigo}/programaciones") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PROGRAMACION_CREAR') and hasAuthority('FISCALIZADOR_ASIGNAR')")
    public ProgramacionFiscalizacionResponse programar(@PathVariable String codigo,@Valid @RequestBody ProgramacionRequest request,Authentication auth){return service.programar(codigo,request,auth.getName());}
}

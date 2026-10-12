package pe.gob.munisanmiguel.controller;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.gob.munisanmiguel.dto.ApiDtos.*;
import pe.gob.munisanmiguel.service.AltaFiscalizacionService;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController @RequestMapping("/api/expedientes")
public class FiscalizacionAltaController {
    private final AltaFiscalizacionService service;
    private final pe.gob.munisanmiguel.service.AdminSettingsService settings;
    public FiscalizacionAltaController(AltaFiscalizacionService service,pe.gob.munisanmiguel.service.AdminSettingsService settings){this.service=service;this.settings=settings;}

    @PostMapping(value="/fiscalizaciones",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('EXPEDIENTE_CREAR')")
    public ResponseEntity<ExpedienteResponse> crear(@Valid @RequestPart("fiscalizacion") CrearFiscalizacionRequest request,
            @RequestPart(value="documentos",required=false) List<MultipartFile> documentos,Authentication auth){
        if(request.metodoUbicacion()==null||request.metodoUbicacion().isBlank())
            throw new IllegalArgumentException("Selecciona un método de ubicación habilitado para tu cuenta.");
        var latitude=request.expediente().latitud();var longitude=request.expediente().longitud();
        if(latitude==null||longitude==null||latitude.abs().compareTo(java.math.BigDecimal.valueOf(90))>0||longitude.abs().compareTo(java.math.BigDecimal.valueOf(180))>0)
            throw new IllegalArgumentException("Indica coordenadas válidas para el predio.");
        var preferences=settings.forUser(auth.getName());
        boolean enabled=switch(request.metodoUbicacion()){
            case "MAPA_INTERNO" -> preferences.mapaInterno();
            case "GOOGLE_EXTERNO" -> preferences.googleExterno();
            case "GOOGLE_URL" -> preferences.googleUrl();
            default -> false;
        };
        if(!enabled)throw new AccessDeniedException("El método de ubicación está desactivado para tu cuenta.");
        if("GOOGLE_URL".equals(request.metodoUbicacion())){
            var point=pe.gob.munisanmiguel.service.GoogleMapsCoordinates.extract(request.enlaceGoogle());
            if(point==null||point[0].compareTo(latitude)!=0||point[1].compareTo(longitude)!=0)
                throw new IllegalArgumentException("El enlace de Google Maps no corresponde a las coordenadas confirmadas.");
        }
        if(request.primeraProgramacion()!=null&&(!has(auth,"PROGRAMACION_CREAR")||!has(auth,"FISCALIZADOR_ASIGNAR")))
            throw new AccessDeniedException("No tienes permisos para programar y asignar fiscalizadores.");
        if(documentos!=null&&!documentos.isEmpty()&&!has(auth,"EXPEDIENTE_EDITAR"))
            throw new AccessDeniedException("No tienes permiso para adjuntar documentos al expediente.");
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request,documentos,auth.getName()));
    }

    @GetMapping("/{codigo}/documentos") @PreAuthorize("hasAuthority('EXPEDIENTE_VER')")
    public List<ExpedienteDocumentoResponse> documentos(@PathVariable String codigo){return service.listarDocumentos(codigo);}

    @PostMapping(value="/{codigo}/documentos",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('EXPEDIENTE_EDITAR')")
    public List<ExpedienteDocumentoResponse> adjuntar(@PathVariable String codigo,
            @RequestPart("documentos") List<MultipartFile> archivos,Authentication auth){
        return service.adjuntar(codigo,archivos,auth.getName());
    }

    @PostMapping(value="/{codigo}/actas-adjuntas",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('EXPEDIENTE_EDITAR')")
    public List<ExpedienteDocumentoResponse> adjuntarActas(@PathVariable String codigo,
            @RequestPart("actas") List<MultipartFile> archivos,Authentication auth){
        return service.adjuntarActas(codigo,archivos,auth.getName());
    }

    @GetMapping("/{codigo}/documentos/{id}/archivo") @PreAuthorize("hasAuthority('EXPEDIENTE_VER')")
    public ResponseEntity<Resource> archivo(@PathVariable String codigo,@PathVariable Long id){
        var d=service.descargar(codigo,id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.mime()))
                .header(HttpHeaders.CONTENT_DISPOSITION,("application/pdf".equals(d.mime())?ContentDisposition.inline():ContentDisposition.attachment()).filename(d.name(),StandardCharsets.UTF_8).build().toString())
                .body(d.resource());
    }
    private boolean has(Authentication auth,String capability){return auth.getAuthorities().stream().anyMatch(a->capability.equals(a.getAuthority()));}
}

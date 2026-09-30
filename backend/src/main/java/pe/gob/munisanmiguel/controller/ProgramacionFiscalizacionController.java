package pe.gob.munisanmiguel.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.gob.munisanmiguel.dto.ApiDtos.*;
import pe.gob.munisanmiguel.service.ExpedienteFiscalizacionService;
import java.util.List;

@RestController @RequestMapping("/api/programaciones")
public class ProgramacionFiscalizacionController {
    private final ExpedienteFiscalizacionService service;
    public ProgramacionFiscalizacionController(ExpedienteFiscalizacionService service){this.service=service;}

    @PostMapping("/{id}/reprogramar") @PreAuthorize("hasAuthority('PROGRAMACION_REPROGRAMAR') and hasAuthority('FISCALIZADOR_ASIGNAR')")
    public ProgramacionFiscalizacionResponse reprogramar(@PathVariable Long id,@Valid @RequestBody ReprogramarRequest request,Authentication auth){return service.reprogramar(id,request,auth.getName());}
    @PostMapping("/{id}/cancelar") @PreAuthorize("hasAuthority('PROGRAMACION_CANCELAR')")
    public ProgramacionFiscalizacionResponse cancelar(@PathVariable Long id,@Valid @RequestBody CancelarProgramacionRequest request,Authentication auth){return service.cancelar(id,request,auth.getName());}
    @GetMapping("/mis-asignaciones") @PreAuthorize("hasAuthority('TAREAS_PROPIAS_VER')")
    public List<ProgramacionFiscalizacionResponse> misAsignaciones(Authentication auth){return service.misProgramaciones(auth.getName());}
}

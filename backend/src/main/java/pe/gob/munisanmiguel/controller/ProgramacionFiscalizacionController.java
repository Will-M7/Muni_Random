package pe.gob.munisanmiguel.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pe.gob.munisanmiguel.dto.ApiDtos.*;
import pe.gob.munisanmiguel.service.ExpedienteFiscalizacionService;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;
import pe.gob.munisanmiguel.repository.ProgramacionFiscalizacionRepository;

@RestController @RequestMapping("/api/programaciones")
public class ProgramacionFiscalizacionController {
    private final ExpedienteFiscalizacionService service;
    private final ProgramacionFiscalizacionRepository programaciones;
    public ProgramacionFiscalizacionController(ExpedienteFiscalizacionService service,ProgramacionFiscalizacionRepository programaciones){this.service=service;this.programaciones=programaciones;}

    public record AgendaVisita(String fiscalizadorId,String fiscalizadorNombre,LocalTime hora,String expedienteCodigo,String estado){}
    @GetMapping("/agenda") @PreAuthorize("hasAuthority('PROGRAMACION_VER')")
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public List<AgendaVisita> agenda(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fecha){
        return programaciones.findAllByFechaOrderByHoraAscIdAsc(fecha).stream().map(p->
            new AgendaVisita(p.getFiscalizador()==null?null:p.getFiscalizador().getId(),
                p.getFiscalizador()==null?"Sin fiscalizador":p.getFiscalizador().getNombre(),p.getHora(),
                p.getExpediente().getCodigo(),p.getEstadoProgramacion().name())).toList();
    }

    @PostMapping("/{id}/reprogramar") @PreAuthorize("hasAuthority('PROGRAMACION_REPROGRAMAR') and hasAuthority('FISCALIZADOR_ASIGNAR')")
    public ProgramacionFiscalizacionResponse reprogramar(@PathVariable Long id,@Valid @RequestBody ReprogramarRequest request,Authentication auth){return service.reprogramar(id,request,auth.getName());}
    @PostMapping("/{id}/cancelar") @PreAuthorize("hasAuthority('PROGRAMACION_CANCELAR')")
    public ProgramacionFiscalizacionResponse cancelar(@PathVariable Long id,@Valid @RequestBody CancelarProgramacionRequest request,Authentication auth){return service.cancelar(id,request,auth.getName());}
    @GetMapping("/mis-asignaciones") @PreAuthorize("hasAuthority('TAREAS_PROPIAS_VER')")
    public List<ProgramacionFiscalizacionResponse> misAsignaciones(Authentication auth){return service.misProgramaciones(auth.getName());}
}

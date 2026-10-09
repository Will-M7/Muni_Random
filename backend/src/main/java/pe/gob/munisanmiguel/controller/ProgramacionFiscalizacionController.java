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
    private final pe.gob.munisanmiguel.service.AdminSettingsService settings;
    private final pe.gob.munisanmiguel.service.RevisionFiscalizacionService revisiones;
    public ProgramacionFiscalizacionController(ExpedienteFiscalizacionService service,ProgramacionFiscalizacionRepository programaciones,pe.gob.munisanmiguel.service.AdminSettingsService settings,pe.gob.munisanmiguel.service.RevisionFiscalizacionService revisiones){this.service=service;this.programaciones=programaciones;this.settings=settings;this.revisiones=revisiones;}

    public record AgendaVisita(String fiscalizadorId,String fiscalizadorNombre,LocalTime hora,String expedienteCodigo,String estado){}
    @GetMapping("/agenda") @PreAuthorize("hasAuthority('PROGRAMACION_VER')")
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public List<AgendaVisita> agenda(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fecha){
        return programaciones.findAllByFechaOrderByHoraAscIdAsc(fecha).stream().map(p->
            new AgendaVisita(p.getFiscalizador()==null?null:p.getFiscalizador().getId(),
                p.getFiscalizador()==null?"Sin fiscalizador":p.getFiscalizador().getNombre(),p.getHora(),
                p.getExpediente().getCodigo(),p.getEstadoProgramacion().name())).toList();
    }
    public record DailySummary(LocalDate fecha,long pendientes,long realizadas,long revisionesPendientes,long carga,int limite,String color,java.util.Map<String,Long> porFiscalizador){}
    @GetMapping("/resumen") @PreAuthorize("hasAuthority('PROGRAMACION_VER')")
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public List<DailySummary> resumen(){
        var pendientesRevision=new java.util.HashMap<LocalDate,Long>();
        for(var row:revisiones.pendientes()){
            var value=row.get("fechaProgramada");if(value instanceof LocalDate date)pendientesRevision.merge(date,1L,Long::sum);
        }
        var grouped=programaciones.findAll().stream().filter(p->p.getFecha()!=null).filter(p->p.getEstadoProgramacion()!=pe.gob.munisanmiguel.entity.EstadoProgramacion.CANCELADA&&p.getEstadoProgramacion()!=pe.gob.munisanmiguel.entity.EstadoProgramacion.REPROGRAMADA)
                .collect(java.util.stream.Collectors.groupingBy(pe.gob.munisanmiguel.entity.ProgramacionFiscalizacion::getFecha));
        int limit=settings.dailyLimit();
        return grouped.entrySet().stream().sorted(java.util.Map.Entry.<LocalDate,java.util.List<pe.gob.munisanmiguel.entity.ProgramacionFiscalizacion>>comparingByKey().reversed()).limit(60).map(entry->{
            var list=entry.getValue();long completed=list.stream().filter(p->p.getEstadoProgramacion()==pe.gob.munisanmiguel.entity.EstadoProgramacion.REALIZADA).count();
            long pending=list.stream().filter(p->p.getEstadoProgramacion()==pe.gob.munisanmiguel.entity.EstadoProgramacion.PROGRAMADA||p.getEstadoProgramacion()==pe.gob.munisanmiguel.entity.EstadoProgramacion.EN_CURSO||p.getEstadoProgramacion()==pe.gob.munisanmiguel.entity.EstadoProgramacion.VENCIDA).count();
            var byInspector=list.stream().collect(java.util.stream.Collectors.groupingBy(p->p.getFiscalizador()==null?"Sin fiscalizador":p.getFiscalizador().getNombre(),java.util.stream.Collectors.counting()));
            String color=list.size()>limit?"rojo":list.size()>=0.8*limit?"amarillo":"verde";
            return new DailySummary(entry.getKey(),pending,completed,pendientesRevision.getOrDefault(entry.getKey(),0L),list.size(),limit,color,byInspector);
        }).toList();
    }

    @PostMapping("/{id}/reprogramar") @PreAuthorize("hasAuthority('PROGRAMACION_REPROGRAMAR') and hasAuthority('FISCALIZADOR_ASIGNAR')")
    public ProgramacionFiscalizacionResponse reprogramar(@PathVariable Long id,@Valid @RequestBody ReprogramarRequest request,Authentication auth){return service.reprogramar(id,request,auth.getName());}
    @PostMapping("/{id}/cancelar") @PreAuthorize("hasAuthority('PROGRAMACION_CANCELAR')")
    public ProgramacionFiscalizacionResponse cancelar(@PathVariable Long id,@Valid @RequestBody CancelarProgramacionRequest request,Authentication auth){return service.cancelar(id,request,auth.getName());}
    @GetMapping("/mis-asignaciones") @PreAuthorize("hasAuthority('TAREAS_PROPIAS_VER')")
    public List<ProgramacionFiscalizacionResponse> misAsignaciones(Authentication auth){return service.misProgramaciones(auth.getName());}
}

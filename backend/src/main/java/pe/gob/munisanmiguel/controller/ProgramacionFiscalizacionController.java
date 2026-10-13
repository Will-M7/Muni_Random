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
    private final pe.gob.munisanmiguel.service.MunicipalAccessService access;
    private final ExpedienteFiscalizacionService service;
    private final ProgramacionFiscalizacionRepository programaciones;
    private final pe.gob.munisanmiguel.service.AdminSettingsService settings;
    private final pe.gob.munisanmiguel.service.RevisionFiscalizacionService revisiones;
    public ProgramacionFiscalizacionController(ExpedienteFiscalizacionService service,ProgramacionFiscalizacionRepository programaciones,pe.gob.munisanmiguel.service.AdminSettingsService settings,pe.gob.munisanmiguel.service.RevisionFiscalizacionService revisiones,pe.gob.munisanmiguel.service.MunicipalAccessService access){this.service=service;this.programaciones=programaciones;this.settings=settings;this.revisiones=revisiones;this.access=access;}

    public record AgendaVisita(String fiscalizadorId,String fiscalizadorNombre,LocalTime hora,String expedienteCodigo,String estado,String origen){}
    @GetMapping("/agenda") @PreAuthorize("hasAuthority('PROGRAMACION_VER')")
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public List<AgendaVisita> agenda(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fecha){
        var actor=access.current();return programaciones.findAllByFechaOrderByHoraAscIdAsc(fecha).stream().filter(p->access.agendaVisible(p,actor)).map(p->
            new AgendaVisita(p.getFiscalizador()==null?null:p.getFiscalizador().getId(),
                p.getFiscalizador()==null?"Sin fiscalizador":p.getFiscalizador().getNombre(),p.getHora(),
                p.getExpediente().getCodigo(),p.getEstadoProgramacion().name(),p.getExpediente().getOrigen().name())).toList();
    }
    public record DailySummary(LocalDate fecha,long pendientes,long realizadas,long revisionesPendientes,long carga,int limite,String color,java.util.Map<String,Long> porFiscalizador){}
    @GetMapping("/resumen") @PreAuthorize("hasAuthority('PROGRAMACION_VER')")
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public List<DailySummary> resumen(@RequestParam(required=false) String fiscalizador,@RequestParam(required=false) String origen){
        var pendientesRevision=new java.util.HashMap<LocalDate,Long>();
        for(var row:revisiones.pendientes()){
            var value=row.get("fechaProgramada");
            boolean inspectorOk=fiscalizador==null||fiscalizador.isBlank()||fiscalizador.equals(row.get("fiscalizador"));
            boolean origenOk=origen==null||origen.isBlank()||origen.equals(row.get("origen"));
            if(inspectorOk&&origenOk&&value instanceof LocalDate date)pendientesRevision.merge(date,1L,Long::sum);
        }
        var actor=access.current();var grouped=programaciones.findAll().stream().filter(p->p.getFecha()!=null).filter(p->access.agendaVisible(p,actor))
                .filter(p->p.getEstadoProgramacion()!=pe.gob.munisanmiguel.entity.EstadoProgramacion.CANCELADA&&p.getEstadoProgramacion()!=pe.gob.munisanmiguel.entity.EstadoProgramacion.REPROGRAMADA)
                .filter(p->fiscalizador==null||fiscalizador.isBlank()||(p.getFiscalizador()!=null&&fiscalizador.equals(p.getFiscalizador().getNombre())))
                .filter(p->origen==null||origen.isBlank()||origen.equals(p.getExpediente().getOrigen().name()))
                .collect(java.util.stream.Collectors.groupingBy(pe.gob.munisanmiguel.entity.ProgramacionFiscalizacion::getFecha));
        int limit=settings.dailyLimit();
        return grouped.entrySet().stream().sorted(java.util.Map.Entry.<LocalDate,java.util.List<pe.gob.munisanmiguel.entity.ProgramacionFiscalizacion>>comparingByKey().reversed()).limit(60).map(entry->{
            var list=entry.getValue();long completed=list.stream().filter(p->p.getEstadoProgramacion()==pe.gob.munisanmiguel.entity.EstadoProgramacion.REALIZADA).count();
            long pending=list.stream().filter(p->p.getEstadoProgramacion()==pe.gob.munisanmiguel.entity.EstadoProgramacion.PROGRAMADA).count();
            var byInspector=list.stream().collect(java.util.stream.Collectors.groupingBy(p->p.getFiscalizador()==null?"Sin fiscalizador":p.getFiscalizador().getNombre(),java.util.stream.Collectors.counting()));
            long carga=list.stream().filter(p->MunicipalAgendaController.occupies(p.getEstadoProgramacion())).count();String color=MunicipalAgendaController.color(carga,limit);
            return new DailySummary(entry.getKey(),pending,completed,pendientesRevision.getOrDefault(entry.getKey(),0L),carga,limit,color,byInspector);
        }).toList();
    }

    @PostMapping("/{id}/reprogramar") @PreAuthorize("hasAuthority('PROGRAMACION_REPROGRAMAR') and hasAuthority('FISCALIZADOR_ASIGNAR')")
    public ProgramacionFiscalizacionResponse reprogramar(@PathVariable Long id,@Valid @RequestBody ReprogramarRequest request,Authentication auth){return service.reprogramar(id,request,auth.getName());}
    @PostMapping("/{id}/cancelar") @PreAuthorize("hasAuthority('PROGRAMACION_CANCELAR')")
    public ProgramacionFiscalizacionResponse cancelar(@PathVariable Long id,@Valid @RequestBody CancelarProgramacionRequest request,Authentication auth){return service.cancelar(id,request,auth.getName());}
    @GetMapping("/mis-asignaciones") @PreAuthorize("hasAuthority('TAREAS_PROPIAS_VER')")
    public List<ProgramacionFiscalizacionResponse> misAsignaciones(Authentication auth){return service.misProgramaciones(auth.getName());}
}

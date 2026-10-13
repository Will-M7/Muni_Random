package pe.gob.munisanmiguel.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import pe.gob.munisanmiguel.service.*;
import pe.gob.munisanmiguel.repository.*;
import pe.gob.munisanmiguel.entity.*;
import java.time.*;
import java.util.*;

@RestController @RequestMapping("/api/programaciones/agenda")
public class MunicipalAgendaController {
    public record Visit(Long programacionId,LocalDate fecha,LocalTime hora,String expedienteCodigo,String administrado,String direccion,String referencia,String fiscalizadorId,String fiscalizadorNombre,String estado,String origen){}
    public record Day(LocalDate fecha,long carga,String color){}
    public record Month(String mes,int limite,String alcance,List<Day> dias,List<Visit> visitas){}
    private final ProgramacionFiscalizacionRepository visits;private final AdminSettingsService settings;private final MunicipalAccessService access;
    public MunicipalAgendaController(ProgramacionFiscalizacionRepository visits,AdminSettingsService settings,MunicipalAccessService access){this.visits=visits;this.settings=settings;this.access=access;}
    public static boolean occupies(EstadoProgramacion state){return state==EstadoProgramacion.PROGRAMADA||state==EstadoProgramacion.EN_CURSO||state==EstadoProgramacion.VENCIDA;}
    public static String color(long count,int limit){return count==0?"neutro":count>limit?"rojo":count*5L>=limit*4L?"amarillo":"verde";}
    @GetMapping("/mensual") @PreAuthorize("hasAuthority('PROGRAMACION_VER')") @Transactional(readOnly=true)
    public Month monthly(@RequestParam String mes,Authentication auth){YearMonth month;try{month=YearMonth.parse(mes);}catch(Exception e){throw new IllegalArgumentException("Indica un mes válido en formato AAAA-MM.");}
        if(month.getYear()<1900||month.getYear()>2200)throw new IllegalArgumentException("El año debe estar entre 1900 y 2200.");
        var actor=access.current();var data=visits.findAllByFechaGreaterThanEqualAndFechaLessThanOrderByFechaAscHoraAscIdAsc(month.atDay(1),month.plusMonths(1).atDay(1)).stream().filter(p->access.agendaVisible(p,actor)).toList();
        int limit=settings.dailyLimit();var counts=new HashMap<LocalDate,Long>();for(var p:data)if(occupies(p.getEstadoProgramacion()))counts.merge(p.getFecha(),1L,Long::sum);
        var days=new ArrayList<Day>();for(int day=1;day<=month.lengthOfMonth();day++){var date=month.atDay(day);long count=counts.getOrDefault(date,0L);days.add(new Day(date,count,color(count,limit)));}
        var rows=data.stream().map(p->{var e=p.getExpediente();var f=p.getFiscalizador();return new Visit(p.getId(),p.getFecha(),p.getHora(),e.getCodigo(),String.join(" ",Objects.toString(e.getNombreAdministrado(),""),Objects.toString(e.getAdministradoApellidos(),"")).trim(),e.getDireccionFiscalizada(),e.getReferenciaDomicilio(),f==null?null:f.getId(),f==null?"Sin fiscalizador":f.getNombre(),p.getEstadoProgramacion().name(),e.getOrigen().name());}).toList();
        return new Month(mes,limit,access.municipal(actor)?"MUNICIPAL":"AUTORIZADO",days,rows);
    }
}

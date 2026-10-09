package pe.gob.munisanmiguel.controller;

import org.junit.jupiter.api.Test;
import pe.gob.munisanmiguel.entity.*;
import pe.gob.munisanmiguel.repository.ProgramacionFiscalizacionRepository;
import pe.gob.munisanmiguel.service.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProgramacionResumenTest {
    private ProgramacionFiscalizacion visit(LocalDate date,EstadoProgramacion state){var p=new ProgramacionFiscalizacion();p.setFecha(date);p.setEstadoProgramacion(state);var f=new Fiscalizador();f.setId("F-1");f.setNombre("Inspector");p.setFiscalizador(f);return p;}
    @Test void excluyeCanceladasYReprogramadasYColoreaSoloLaCarga(){
        var repo=mock(ProgramacionFiscalizacionRepository.class);var settings=mock(AdminSettingsService.class);var reviews=mock(RevisionFiscalizacionService.class);
        var day1=LocalDate.of(2026,10,10);var day2=day1.plusDays(1);
        var visits=new ArrayList<ProgramacionFiscalizacion>();
        for(int i=0;i<4;i++)visits.add(visit(day1,EstadoProgramacion.PROGRAMADA));
        visits.add(visit(day1,EstadoProgramacion.CANCELADA));
        for(int i=0;i<6;i++)visits.add(visit(day2,EstadoProgramacion.PROGRAMADA));
        visits.add(visit(day2,EstadoProgramacion.REPROGRAMADA));
        when(repo.findAll()).thenReturn(visits);when(settings.dailyLimit()).thenReturn(5);
        when(reviews.pendientes()).thenReturn(List.of(Map.of("fechaProgramada",day1),Map.of("fechaProgramada",day2)));
        var controller=new ProgramacionFiscalizacionController(mock(ExpedienteFiscalizacionService.class),repo,settings,reviews);
        var result=controller.resumen();
        var yellow=result.stream().filter(x->x.fecha().equals(day1)).findFirst().orElseThrow();
        var red=result.stream().filter(x->x.fecha().equals(day2)).findFirst().orElseThrow();
        assertEquals(4,yellow.carga());assertEquals("amarillo",yellow.color());assertEquals(1,yellow.revisionesPendientes());
        assertEquals(6,red.carga());assertEquals("rojo",red.color());assertEquals(6,red.porFiscalizador().get("Inspector"));
    }
}

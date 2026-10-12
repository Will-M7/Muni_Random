// @vitest-environment jsdom
import '@angular/compiler';
import { describe, expect, it, vi } from 'vitest';
import { FiscalizadorComponent } from './fiscalizador';
import { FiscalizadorTarea } from '../../services/verificacion.service';
import { todayPeru } from '../../core/date';

function tarea(codigo:string,hora:string,latitud:number|null,longitud:number|null):FiscalizadorTarea {
  return {codigo,hora,latitud,longitud,resultado:'PENDIENTE',direccion:'Jr. Prueba',ciudadano:'Persona'} as FiscalizadorTarea;
}

describe('recorrido del fiscalizador',()=>{
  const portal=(service:any={},router:any={})=>new FiscalizadorComponent(service as never,{} as never,router as never,{} as never,{markForCheck:vi.fn()} as never,{} as never);
  it('muestra puntos y visitas sin coordenadas antes de solicitar GPS',()=>{
    const x=portal();x.tareasDia=[tarea('B','10:00',-15.4,-70.1),tarea('A','08:00',-15.3,-70.2),tarea('C','09:00',null,null)];
    expect(x.rutaOrdenada.map(t=>t.codigo)).toEqual(['A','B']);
    expect(x.sinCoordenadas.map(t=>t.codigo)).toEqual(['C']);
  });
  it('recalcula desde la posición real y retira una visita concluida',()=>{
    const x=portal();x.tareasDia=[tarea('A','08:00',-15.3,-70.2),tarea('B','10:00',-15.4,-70.1)];
    x.ubicacionActual=[-15.4,-70.1];expect(x.rutaOrdenada[0].codigo).toBe('B');
    x.tareasDia[1].resultado='REALIZADA';expect(x.rutaOrdenada.map(t=>t.codigo)).toEqual(['A']);
  });
  it('Mapa desplaza al mapa, centra y abre el popup del punto exacto',async()=>{
    const x=portal();const task=tarea('FIS-MAPA','09:30',-12.04,-77.03);task.fecha='2026-10-09';
    const scrollIntoView=vi.fn();(x as any).routeMap={nativeElement:{scrollIntoView}};
    const setView=vi.fn();x.mapa={setView} as any;x.tareasDia=[task];
    (x as any).actualizarMapa=vi.fn();(x as any).abrirPopupTarea=vi.fn();x.verEnMapa(task);
    await new Promise(resolve=>setTimeout(resolve,5));
    expect(scrollIntoView).toHaveBeenCalledWith({behavior:'smooth',block:'center'});
    expect(setView).toHaveBeenCalledWith([-12.04,-77.03],16);expect((x as any).abrirPopupTarea).toHaveBeenCalledWith(task);
  });
  it('Inicia una visita vigente sin pedir GPS antes de abrir el reporte',async()=>{
    const start=vi.fn().mockResolvedValue({id:4});const navigate=vi.fn();const x=portal({iniciarDiligencia:start},{navigate});
    const task=tarea('FIS-START','10:00',null,null);Object.assign(task,{esExpediente:true,fecha:todayPeru(),programacionId:8,estadoProgramacion:'PROGRAMADA'});
    await x.iniciarDesdeAgenda(task);
    expect(start).toHaveBeenCalledWith(8,{estadoGps:'NO_DISPONIBLE'});expect(navigate).toHaveBeenCalledWith(['/fiscalizador/diligencia',8]);
  });
});

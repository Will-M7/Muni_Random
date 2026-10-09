// @vitest-environment jsdom
import '@angular/compiler';
import { describe, expect, it } from 'vitest';
import { FiscalizadorComponent } from './fiscalizador';
import { FiscalizadorTarea } from '../../services/verificacion.service';

function tarea(codigo:string,hora:string,latitud:number|null,longitud:number|null):FiscalizadorTarea {
  return {codigo,hora,latitud,longitud,resultado:'PENDIENTE',direccion:'Jr. Prueba',ciudadano:'Persona'} as FiscalizadorTarea;
}

describe('recorrido del fiscalizador',()=>{
  const portal=()=>new FiscalizadorComponent({} as never,{} as never,{} as never,{} as never,{} as never,{} as never);
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
});

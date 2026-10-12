// @vitest-environment jsdom
import '@angular/compiler';
import { describe, expect, it, vi } from 'vitest';
import { DiligenciaComponent } from './diligencia';

describe('inicio de diligencia sin GPS obligatorio',()=>{
  it('registra el inicio con la hora del backend y habilita el reporte sin solicitar ubicación',async()=>{
    const getCurrentPosition=vi.fn();vi.stubGlobal('navigator',{geolocation:{getCurrentPosition}});
    const workspace:any={id:21,estadoDiligencia:'INICIADA',estadoProgramacion:'EN_CURSO',situacionPersona:'PENDIENTE',medioEntrega:'PENDIENTE',copiaEntregada:false};
    const api={iniciarDiligencia:vi.fn().mockResolvedValue(workspace)};
    const route={snapshot:{paramMap:{get:()=> '9'}}};const cdr={markForCheck:vi.fn()};
    const component=new DiligenciaComponent(route as any,api as any,cdr as any);
    component.programacionId=9;
    await component.iniciar();
    expect(api.iniciarDiligencia).toHaveBeenCalledWith(9,{estadoGps:'NO_DISPONIBLE'});
    expect(getCurrentPosition).not.toHaveBeenCalled();expect(component.puedeEditar).toBe(true);
    expect(component.w?.id).toBe(21);
    vi.unstubAllGlobals();
  });
});

describe('finalización con hechos opcionales',()=>{
  it('pide confirmación antes de continuar si el acta quedará sin hechos',async()=>{
    const api={guardarDiligencia:vi.fn(),finalizarDiligencia:vi.fn()};
    const component=new DiligenciaComponent({snapshot:{paramMap:{get:()=> '9'}}} as any,api as any,{markForCheck:vi.fn()} as any);
    component.w={id:31,estadoDiligencia:'EN_DESARROLLO',estadoProgramacion:'EN_CURSO'} as any;
    const confirm=vi.fn().mockReturnValue(false);vi.stubGlobal('confirm',confirm);
    await component.finalizar();
    expect(confirm).toHaveBeenCalled();expect(api.guardarDiligencia).not.toHaveBeenCalled();expect(api.finalizarDiligencia).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });
});

describe('adjuntos sucesivos de evidencias',()=>{
  it('limpia el selector y conserva documentos agregados en cargas separadas',async()=>{
    const persisted:any[]=[];
    const workspace:any={id:31,estadoDiligencia:'EN_DESARROLLO',estadoProgramacion:'EN_CURSO',situacionPersona:'PENDIENTE',evidencias:persisted};
    const api={guardarDiligencia:vi.fn().mockResolvedValue({estadoDiligencia:'EN_DESARROLLO',estadoProgramacion:'EN_CURSO'}),agregarEvidenciaDiligencia:vi.fn().mockImplementation(async(_id:number,_tipo:string,_descripcion:string,file:File)=>{persisted.push({id:persisted.length+1,nombreOriginal:file.name,fechaHora:'2026-10-09T09:00:00',autor:'fiscal'});}),obtenerEspacioDiligencia:vi.fn().mockImplementation(async()=>({...workspace,evidencias:[...persisted]}))};
    const component=new DiligenciaComponent({snapshot:{paramMap:{get:()=> '9'}}} as any,api as any,{markForCheck:vi.fn()} as any);
    component.w=workspace;
    for(const name of ['acta-1.pdf','acta-2.pdf']){
      const input:any={files:[new File(['%PDF-1.4'],name,{type:'application/pdf'})],value:name};
      component.seleccionarEvidencia({target:input} as any);
      await component.subirEvidencia();
      expect(input.value).toBe('');
    }
    expect(api.agregarEvidenciaDiligencia).toHaveBeenCalledTimes(2);
    expect(component.w?.evidencias.map(e=>e.nombreOriginal)).toEqual(['acta-1.pdf','acta-2.pdf']);
  });
  it('acumula selecciones, permite quitar una pendiente y rechaza once por envío',()=>{
    const component=new DiligenciaComponent({} as any,{} as any,{markForCheck:vi.fn()} as any);
    const input=(names:string[])=>({target:{files:names.map(name=>new File(['%PDF-1.4'],name,{type:'application/pdf'})),value:'elegido'}} as any);
    component.seleccionarEvidencia(input(['uno.pdf']));
    component.seleccionarEvidencia(input(['dos.pdf','tres.pdf']));
    expect(component.evidenciaArchivos.map(f=>f.name)).toEqual(['uno.pdf','dos.pdf','tres.pdf']);
    component.quitarEvidenciaPendiente(1);
    expect(component.evidenciaArchivos.map(f=>f.name)).toEqual(['uno.pdf','tres.pdf']);
    component.seleccionarEvidencia(input(Array.from({length:9},(_,i)=>`extra-${i}.pdf`)));
    expect(component.evidenciaArchivos).toHaveLength(2);
    expect(component.error).toContain('hasta 10 archivos');
  });
  it('retira con motivo solo una evidencia editable y actualiza el listado',async()=>{
    const saved:any[]=[{id:3,nombreOriginal:'equivocado.pdf'}];
    const api={retirarEvidenciaDiligencia:vi.fn().mockImplementation(async()=>{saved.splice(0,1);}),obtenerEspacioDiligencia:vi.fn().mockImplementation(async()=>({id:31,estadoDiligencia:'EN_DESARROLLO',evidencias:[...saved]}))};
    const component=new DiligenciaComponent({snapshot:{paramMap:{get:()=> '9'}}} as any,api as any,{markForCheck:vi.fn()} as any);
    component.w={id:31,estadoDiligencia:'EN_DESARROLLO',evidencias:[...saved]} as any;
    vi.spyOn(window,'confirm').mockReturnValue(true);vi.spyOn(window,'prompt').mockReturnValue('Archivo equivocado');
    await component.retirarEvidencia(3,'equivocado.pdf');
    expect(api.retirarEvidenciaDiligencia).toHaveBeenCalledWith(31,3,'Archivo equivocado');
    expect(component.w?.evidencias).toHaveLength(0);
    vi.restoreAllMocks();
  });
});

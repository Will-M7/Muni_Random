import { EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ExpedienteDetalleComponent } from './expediente-detalle';
import { UsuariosService } from '../../services/usuarios.service';
import { AuthService } from '../../services/auth.service';
import { vi } from 'vitest';

describe('historial breve del expediente',()=>{
  it('presenta la hora local de Lima y una acción comprensible',()=>{
    TestBed.configureTestingModule({providers:[{provide:UsuariosService,useValue:{}},{provide:AuthService,useValue:{hasCapability:()=>true}}]});
    const component=runInInjectionContext(TestBed.inject(EnvironmentInjector),()=>new ExpedienteDetalleComponent({} as any,{} as any,{} as any,{markForCheck:()=>{}} as any));
    expect(component.nombreEvento('PROGRAMACION_CREADA')).toBe('Visita programada');
    expect(component.fechaHistorial('2026-10-09T06:29:00')).toBe('09/10/2026 06:29');
    TestBed.resetTestingModule();
  });
  it('muestra cinco eventos recientes y permite ver todos sin alterar el historial recibido',()=>{
    TestBed.configureTestingModule({providers:[{provide:UsuariosService,useValue:{}},{provide:AuthService,useValue:{hasCapability:()=>true}}]});
    const component=runInInjectionContext(TestBed.inject(EnvironmentInjector),()=>new ExpedienteDetalleComponent({} as any,{} as any,{} as any,{markForCheck:()=>{}} as any));
    const events=Array.from({length:7},(_,i)=>({tipoEvento:'EVIDENCIA_AGREGADA',fechaHora:`2026-10-09T0${i}:00:00`,actor:'fiscalizador'}));
    component.expediente={historial:events} as any;
    expect(component.historialVisible).toHaveLength(5);
    expect(component.historialVisible[0].fechaHora).toBe('2026-10-09T06:00:00');
    component.historialCompleto=true;
    expect(component.historialVisible).toHaveLength(7);
    component.historialCompleto=false;
    expect(component.historialVisible).toHaveLength(5);
    expect(component.expediente?.historial).toHaveLength(7);
    expect(component.nombreEvento('EVIDENCIA_RETIRADA')).toBe('Evidencia retirada');
    TestBed.resetTestingModule();
  });
  it('acumula documentos y distingue actas municipales sin imponer un máximo histórico',async()=>{
    TestBed.configureTestingModule({providers:[{provide:UsuariosService,useValue:{}},{provide:AuthService,useValue:{hasCapability:()=>true}}]});
    let saved:any[]=[];
    const api={adjuntarDocumentosExpediente:vi.fn(async(_code:string,files:File[])=>{saved=[...saved,...files.map((file,i)=>({id:saved.length+i+1,nombre:file.name,categoria:'DOCUMENTO',tamanoBytes:file.size}))];return saved;}),adjuntarActasExpediente:vi.fn(async(_code:string,files:File[])=>{saved=[...saved,...files.map((file,i)=>({id:saved.length+i+1,nombre:file.name,categoria:'ACTA_ADJUNTA',tamanoBytes:file.size}))];return saved;}),obtenerExpediente:vi.fn().mockResolvedValue({codigo:'FIS-TEST',historial:[]})};
    const component=runInInjectionContext(TestBed.inject(EnvironmentInjector),()=>new ExpedienteDetalleComponent({} as any,{} as any,api as any,{markForCheck:()=>{}} as any));
    component.expediente={codigo:'FIS-TEST',historial:[]} as any;
    const selection=(names:string[])=>({target:{files:names.map(name=>new File(['%PDF-1.4'],name,{type:'application/pdf'})),value:'seleccionado'}} as any);
    component.seleccionarDocumentos(selection(['uno.pdf','dos.pdf']));await component.adjuntarDocumentos();
    component.seleccionarDocumentos(selection(['tres.pdf','cuatro.pdf','cinco.pdf']));await component.adjuntarDocumentos();
    component.seleccionarActas(selection(['acta.pdf']));await component.adjuntarActas();
    expect(component.documentosGenerales).toHaveLength(5);
    expect(component.actasAdjuntas.map(d=>d.nombre)).toEqual(['acta.pdf']);
    expect(api.adjuntarDocumentosExpediente).toHaveBeenCalledTimes(2);
    TestBed.resetTestingModule();
  });
});

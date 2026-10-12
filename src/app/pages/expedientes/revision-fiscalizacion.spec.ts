import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { expect, vi } from 'vitest';
import { RevisionBandejaComponent, RevisionDetalleComponent } from './revision-fiscalizacion';
import { VerificacionService } from '../../services/verificacion.service';
import { UsuariosService } from '../../services/usuarios.service';
import { EnvironmentInjector, runInInjectionContext } from '@angular/core';

describe('RevisionBandejaComponent filtros', () => {
  let api: any;
  let fixture: any;
  beforeEach(async () => {
    api = {
      revisionesPendientes: vi.fn().mockResolvedValue([
        { diligenciaId: 1, fechaCierre: '2026-10-08T09:30:00', fechaProgramada: '2026-10-07', fiscalizador: 'Ana', origen: 'DENUNCIA', resultado: 'REALIZADA', estadoRevision: 'PENDIENTE' },
        { diligenciaId: 2, fechaCierre: '2026-10-07T11:00:00', fechaProgramada: '2026-10-07', fiscalizador: 'Luis', origen: 'SOLICITUD', resultado: 'REALIZADA', estadoRevision: 'EN_REVISION' },
      ]),
      resumenDiario: vi.fn(async (filters?: any) => filters?.fiscalizador === 'Ana' ? [] : [{ fecha: '2026-10-07', pendientes: 1, realizadas: 1, revisionesPendientes: 1, carga: 2, limite: 5, color: 'verde', porFiscalizador: { Ana: 1, Luis: 1 } }]),
      agendaProgramacion: vi.fn().mockResolvedValue([]),
    };
    await TestBed.configureTestingModule({ imports: [RevisionBandejaComponent], providers: [{ provide: VerificacionService, useValue: api }, provideRouter([])] }).compileComponents();
    fixture = TestBed.createComponent(RevisionBandejaComponent); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
  });
  it('filtra diligencias por fecha efectiva y no por fecha programada', () => {
    const component = fixture.componentInstance;
    component.fecha = '2026-10-08';
    expect(component.filtrados.map((x: any) => x.diligenciaId)).toEqual([1]);
    component.fecha = '';
    component.fiscalizador = 'Luis';
    expect(component.filtrados.map((x: any) => x.diligenciaId)).toEqual([2]);
    expect(component.fiscalizadores).toEqual(['Ana', 'Luis']);
  });
  it('actualiza el resumen con fiscalizador/origen y permite quitar la fecha seleccionada', async () => {
    const component = fixture.componentInstance;
    component.fiscalizador = 'Ana'; await component.filtrosAgendaCambiaron();
    expect(api.resumenDiario).toHaveBeenLastCalledWith({ fiscalizador: 'Ana', origen: '' });
    expect(component.resumen).toEqual([]);
    component.agendaFecha = '2026-10-07'; component.agendaSeleccionada = [{}]; component.quitarFechaAgenda();
    expect(component.agendaFecha).toBe(''); expect(component.agendaSeleccionada).toEqual([]);
  });
  it('formatea fechas locales sin convertirlas a UTC', () => {
    expect(fixture.componentInstance.fechaCorta('2026-10-08')).toBe('08/10/2026');
    expect(fixture.componentInstance.fechaHoraLocal('2026-10-08T01:15:00')).toBe('08/10/2026 01:15');
  });
  it('omite el panel inferior cuando no hay diligencias y conserva el listado cuando existen', async () => {
    const isolated = TestBed.createComponent(RevisionBandejaComponent);
    const component = isolated.componentInstance;
    vi.spyOn(component, 'ngOnInit').mockResolvedValue();
    component.items = []; component.cargando = false; isolated.detectChanges();
    expect(isolated.nativeElement.querySelector('.review-list')).toBeNull();
    expect(isolated.nativeElement.textContent).not.toContain('No hay diligencias finalizadas pendientes de revisión');
    component.items = [{ diligenciaId: 9, expedienteCodigo: 'FIS-9', estadoRevision: 'PENDIENTE', resultado: 'REALIZADA' }];
    component.cargando = false; (component as any).cdr.markForCheck(); isolated.detectChanges();
    expect(isolated.nativeElement.querySelector('.review-list .review-row')).toBeTruthy();
  });
});

describe('RevisionDetalleComponent campos complementarios',()=>{
  it('permite concluir sin textos cuando se eligió una conclusión',async()=>{
    TestBed.configureTestingModule({providers:[{provide:UsuariosService,useValue:{}}]});
    const api={concluirRevision:vi.fn().mockResolvedValue({}),revision:vi.fn().mockResolvedValue({revision:{id:7,estado:'FINALIZADA'},aclaraciones:[]})};
    const component=runInInjectionContext(TestBed.inject(EnvironmentInjector),()=>new RevisionDetalleComponent({snapshot:{paramMap:{get:()=> '7'}}} as any,api as any,{markForCheck:()=>{}} as any));
    component.data={revision:{id:7,estado:'EN_REVISION'},aclaraciones:[]};
    component.cambiarConclusion('OTRA');
    expect(component.formularioValido).toBe(true);
    await component.concluir();
    expect(api.concluirRevision).toHaveBeenCalledWith(7,expect.objectContaining({fundamentoConclusion:'',descripcionConclusion:'',denominacionOtra:''}));
    TestBed.resetTestingModule();
  });
});

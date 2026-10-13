import { TestBed } from '@angular/core/testing';
import { vi, expect } from 'vitest';
import { AdminComponent } from './admin';
import { UsuariosService } from '../../services/usuarios.service';
import { AuthService } from '../../services/auth.service';

describe('AdminComponent ajustes individuales', () => {
  const users = [
    { username: 'municipio1', nombre: 'Municipio Uno', rol: 'MUNICIPIO', roles: ['MUNICIPIO'], activo: true, archivado: false, fechaCreacion: '', fiscalizadorId: null, fiscalizadorNombre: null },
    { username: 'fiscalizador1', nombre: 'Fiscalizador Uno', rol: 'FISCALIZADOR', roles: ['FISCALIZADOR'], activo: true, archivado: false, fechaCreacion: '', fiscalizadorId: 'f1', fiscalizadorNombre: 'Fiscalizador Uno' },
  ] as any[];
  let service: any;
  let fixture: any;

  beforeEach(async () => {
    service = {
      municipalesGestion:vi.fn().mockResolvedValue([users[0]]), gestionFiscalizadores:vi.fn().mockResolvedValue([]),asignarResponsableMunicipal:vi.fn(), listar: vi.fn().mockResolvedValue(users), integracion: vi.fn().mockResolvedValue({ provider: 'DISABLED', endpoint: null, credentialConfigured: false, actualizadoPor: null, actualizadoEn: null }),
      limiteDiario: vi.fn().mockResolvedValue(5), probarDni: vi.fn().mockResolvedValue({ dni: '00000000', nombres: 'FICTICIO', apellidoPaterno: 'PRUEBA', apellidoMaterno: '', provider: 'PERUAPI_COM', message: 'Consulta realizada correctamente.' }), preferencias: vi.fn(async (username: string) => username === 'municipio1'
        ? { mapaInterno: false, googleExterno: true, googleUrl: false, descargarJornadaHtml: true }
        : { mapaInterno: true, googleExterno: false, googleUrl: true, descargarJornadaHtml: false }),
      guardarPreferencias: vi.fn(async (_username: string, value: any) => ({ ...value })), cuotaDni: vi.fn().mockResolvedValue({ applies: true, provider: 'PERUAPI_COM', date: '2026-10-09', used: 44, limit: 45, remaining: 1, exhausted: false, resetsAt: '2026-10-10T00:00:00-05:00', zone: 'America/Lima' }),
    };
    await TestBed.configureTestingModule({ imports: [AdminComponent], providers: [
      { provide: UsuariosService, useValue: service }, { provide: AuthService, useValue: { token: null } },
    ] }).compileComponents();
    fixture = TestBed.createComponent(AdminComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('abre, cierra inmediatamente y vuelve a abrir ajustes sin mezclar cuentas', async () => {
    const click = (label: string) => {
      const button = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) => b.textContent.trim() === label);
      button.click(); fixture.detectChanges(); return button;
    };
    click('Ajustes'); await fixture.whenStable(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.inline-settings')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.inline-settings input[type="checkbox"]').checked).toBe(false);
    click('Cerrar ajustes');
    expect(fixture.nativeElement.querySelector('.inline-settings')).toBeNull();
    const fiscalizerButton = fixture.nativeElement.querySelectorAll('.user-card')[1].querySelector('.row-actions button:nth-child(2)');
    fiscalizerButton.click(); await fixture.whenStable(); fixture.detectChanges();
    expect(service.preferencias).toHaveBeenLastCalledWith('fiscalizador1');
    expect(fixture.nativeElement.querySelector('.inline-settings input[type="checkbox"]').checked).toBe(false);
  });

  it('guarda las preferencias de la cuenta abierta', async () => {
    const button = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) => b.textContent.trim() === 'Ajustes') as HTMLButtonElement;
    button.click(); await fixture.whenStable(); fixture.detectChanges();
    fixture.nativeElement.querySelector('.inline-settings input[type="checkbox"]').click(); fixture.detectChanges();
    fixture.nativeElement.querySelector('.inline-settings button.primary').click(); await fixture.whenStable();
    expect(service.guardarPreferencias).toHaveBeenCalledWith('municipio1', expect.objectContaining({ mapaInterno: true }));
  });
  it('muestra el resultado de la consulta DNI administrativa usando solo el DNI', async () => {
    const component = fixture.componentInstance;
    component.seccion = 'configuracion'; component.dniPrueba = '00000000';
    await component.probarDni(); fixture.detectChanges();
    expect(service.probarDni).toHaveBeenCalledWith('00000000');
    expect(component.resultadoDni?.provider).toBe('PERUAPI_COM');
    expect(component.resultadoDni?.message).toContain('correctamente');
    expect(fixture.nativeElement.textContent).toContain('Probar consulta DNI');
    expect(fixture.nativeElement.textContent).toContain('Consultar DNI');
  });

  it('permite configurar y probar Factiliza sin mostrar el token guardado', async () => {
    const component = fixture.componentInstance;
    service.guardarIntegracion = vi.fn().mockResolvedValue({ provider: 'FACTILIZA', endpoint: null, credentialConfigured: true, actualizadoPor: 'admin', actualizadoEn: null });
    service.probarDni.mockResolvedValueOnce({ dni: '00000000', nombres: 'NOMBRE', apellidoPaterno: 'PATERNO', apellidoMaterno: 'MATERNO', provider: 'FACTILIZA', message: 'Consulta realizada correctamente.' });
    component.seccion = 'configuracion'; component.integracion.provider = 'FACTILIZA'; component.cambiarProveedorDni(); component.apiKey = 'token-ficticio';
    await component.guardarIntegracion(); fixture.detectChanges();
    expect(service.guardarIntegracion).toHaveBeenCalledWith(expect.objectContaining({ provider: 'FACTILIZA', apiKey: 'token-ficticio' }));
    expect(component.apiKey).toBe('');
    expect(fixture.nativeElement.querySelector('input[name="apiKey"]').value).toBe('');
    expect(fixture.nativeElement.textContent).not.toContain('token-ficticio');
    component.dniPrueba = '00000000'; await component.probarDni(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.dni-test-result').textContent).toContain('Factiliza');
  });

});

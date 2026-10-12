import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { expect, vi } from 'vitest';
import { ExpedienteNuevoComponent } from './expediente-nuevo';
import { VerificacionService } from '../../services/verificacion.service';
import { ReniecService } from '../../services/reniec.service';
import { UsuariosService } from '../../services/usuarios.service';

describe('ExpedienteNuevoComponent consulta manual de DNI', () => {
  let fixture: any;
  let reniec: any;
  let component: ExpedienteNuevoComponent;
  let api: any;
  beforeEach(async () => {
    reniec = { consultarDni: vi.fn().mockReturnValue(of({ dni: '00000000', nombres: 'NOMBRE FICTICIO', apellidoPaterno: 'APELLIDO', apellidoMaterno: 'PRUEBA' })) };
    api = { obtenerFiscalizadores: vi.fn().mockResolvedValue([]), crearFiscalizacion: vi.fn().mockResolvedValue({ codigo: 'FIS-TEST' }), resolverGoogleMaps: vi.fn() };
    const users = { misPreferencias: vi.fn().mockResolvedValue({ mapaInterno: true, googleExterno: true, googleUrl: true, descargarJornadaHtml: true }), limiteDiario: vi.fn().mockResolvedValue(5), cuotaDni: vi.fn().mockResolvedValue({ applies: true, provider: 'PERUAPI_COM', date: '2026-10-09', used: 44, limit: 45, remaining: 1, exhausted: false, resetsAt: '2026-10-10T00:00:00-05:00', zone: 'America/Lima' }) };
    await TestBed.configureTestingModule({ imports: [ExpedienteNuevoComponent], providers: [
      { provide: VerificacionService, useValue: api }, { provide: ReniecService, useValue: reniec }, { provide: UsuariosService, useValue: users }, provideRouter([]),
    ] }).compileComponents();
    fixture = TestBed.createComponent(ExpedienteNuevoComponent); fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    component = fixture.componentInstance;
  });

  it('conserva el tipo de visita y los datos del borrador al regresar del mapa', () => {
    sessionStorage.setItem('sm_expediente_nuevo_borrador', JSON.stringify({tipoVisita:'INOPINADA',programar:true,direccion:'Jr. Tacna 123',nombres:'Nombre conservado'}));
    try {
      component.restaurarBorrador();
      expect(component.tipoVisita).toBe('INOPINADA');
      expect(component.programar).toBe(true);
      expect(component.direccion).toBe('Jr. Tacna 123');
      expect(component.nombres).toBe('Nombre conservado');
    } finally { sessionStorage.removeItem('sm_expediente_nuevo_borrador'); }
  });

  it('mantiene visible el botón y no consulta automáticamente al cambiar el DNI', () => {
    component.dni = '00000000'; component.dniActualizado(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.dni-group button')?.textContent).toContain('Consultar DNI');
    expect(reniec.consultarDni).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.dni-group input').disabled).toBe(false);
  });

  it('valida 8 dígitos, consulta con clic y solicita confirmación antes de sobrescribir', async () => {
    component.dni = '12'; await component.consultarDni();
    expect(reniec.consultarDni).not.toHaveBeenCalled(); expect(component.reniecMensaje).toContain('8 dígitos');
    component.dni = '00000000'; component.nombres = 'Nombre manual'; component.apellidos = 'Apellido manual';
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    await component.consultarDni();
    expect(confirm).toHaveBeenCalled(); expect(component.nombres).toBe('Nombre manual'); expect(component.apellidos).toBe('Apellido manual');
    confirm.mockReturnValue(true); await component.consultarDni();
    expect(component.nombres).toBe('NOMBRE FICTICIO'); expect(component.apellidos).toBe('APELLIDO PRUEBA'); confirm.mockRestore();
  });

  it('permite reintentar después de un error y consultar el nuevo DNI', async () => {
    reniec.consultarDni.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503, error: { message: 'Proveedor temporalmente no disponible.' } })));
    reniec.consultarDni.mockReturnValueOnce(of({ dni: '00000000', nombres: 'NUEVO', apellidoPaterno: 'REGISTRO', apellidoMaterno: '' }));
    component.dni = '00000000'; await component.consultarDni();
    expect(component.consultandoDni).toBe(false); expect(component.reniecMensaje).toContain('temporalmente');
    await component.consultarDni(); expect(reniec.consultarDni).toHaveBeenCalledTimes(2); expect(component.nombres).toBe('NUEVO');
    component.dni = '00000001'; component.dniActualizado(); await component.consultarDni();
    expect(reniec.consultarDni).toHaveBeenLastCalledWith('00000001'); expect(component.nombres).toBe('NUEVO');
  });

  it('deshabilita la consulta cuando se agota la cuota y conserva el ingreso manual', async () => {
    component.cuotaDni = { applies: true, provider: 'PERUAPI_COM', date: '2026-10-09', used: 45, limit: 45, remaining: 0, exhausted: true, resetsAt: '2026-10-10T00:00:00-05:00', zone: 'America/Lima' };
    component.dni = '00000000'; fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.dni-group button').disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('input[name="nombres"]').disabled).toBe(false);
    await component.consultarDni();
    expect(component.reniecMensaje).toContain('límite diario de 45');
    expect(reniec.consultarDni).not.toHaveBeenCalled();
  });

  it('crea expediente de origen SOLICITUD sin enviar vínculo legacy', async () => {
    component.origen = 'SOLICITUD'; component.solicitudCodigo = 'SOL-LEGACY'; component.dependenciaProcedencia = 'Gerencia Municipal'; component.referenciaSolicitud = 'MEMO-123';
    component.objetoFiscalizacion = 'Verificar licencia'; component.direccion = 'Jr. Principal 123'; component.administradoAplica = false;
    component.latitud = -15.4; component.longitud = -70.1; component.ubicacionConfirmada = true; component.metodoUbicacion = 'MAPA_INTERNO';
    await component.guardar();
    expect(api.crearFiscalizacion).toHaveBeenCalled();
    expect(api.crearFiscalizacion.mock.calls[0][0].expediente.solicitudCodigo).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Solicitud legacy vinculada');
  });

  it('muestra solo los métodos habilitados para esta cuenta', () => {
    component.preferencias = { mapaInterno: false, googleExterno: true, googleUrl: false, descargarJornadaHtml: true };
    fixture.detectChanges();
    const methods = fixture.nativeElement.querySelector('.location-methods').textContent;
    expect(methods).toContain('Seleccionar con Google Maps');
    expect(methods).not.toContain('Seleccionar en mapa interno');
    expect(fixture.nativeElement.querySelector('input[name="googleLink"]')).toBeNull();
  });

  it('valida coordenadas copiadas y confirma el punto solo después de la vista previa', () => {
    vi.spyOn((component as any).cdr, 'detectChanges').mockImplementation(() => {});
    component.preferencias.googleExterno = true;
    component.coordenadasGoogle = '91, -70'; component.previsualizarCoordenadasGoogle();
    expect(component.googleError).toContain('coordenadas válidas'); expect(component.ubicacionPendiente).toBeNull();
    component.coordenadasGoogle = '-15.4, -70.1'; component.previsualizarCoordenadasGoogle();
    expect(component.ubicacionConfirmada).toBe(false);
    expect(component.ubicacionPendiente).toEqual({ latitud: -15.4, longitud: -70.1, metodo: 'GOOGLE_EXTERNO' });
    component.confirmarUbicacionPendiente();
    expect(component.ubicacionConfirmada).toBe(true); expect(component.metodoUbicacion).toBe('GOOGLE_EXTERNO');
    expect(component.latitud).toBe(-15.4); expect(component.longitud).toBe(-70.1);
  });

  it('valida URL directa y enlace corto, conserva ubicación hasta confirmar otra', async () => {
    vi.spyOn((component as any).cdr, 'detectChanges').mockImplementation(() => {});
    component.preferencias.googleUrl = true;
    component.googleLink = 'https://example.invalid/place'; await component.confirmarGoogle();
    expect(component.googleError).toContain('No se pudieron verificar');
    component.googleLink = 'https://www.google.com/maps/place/predio/data=!3d-15.4!4d-70.1'; await component.confirmarGoogle();
    expect(component.ubicacionPendiente?.metodo).toBe('GOOGLE_URL');
    component.confirmarUbicacionPendiente();
    component.dni = '12345678'; component.nombres = 'Nombre'; component.archivos = [new File(['x'], 'acta.pdf')];
    component.ajustarUbicacion();
    component.coordenadasGoogle = 'not coordinates'; component.previsualizarCoordenadasGoogle();
    expect(component.ubicacionConfirmada).toBe(true); expect(component.metodoUbicacion).toBe('GOOGLE_URL');
    component.cancelarAjusteUbicacion();
    expect(component.latitud).toBe(-15.4); expect(component.dni).toBe('12345678'); expect(component.nombres).toBe('Nombre'); expect(component.archivos).toHaveLength(1);
    component.googleLink = 'https://maps.app.goo.gl/short';
    api.resolverGoogleMaps.mockResolvedValue('https://www.google.com/maps/place/predio/data=!3d-15.5!4d-70.2');
    await component.confirmarGoogle();
    expect(api.resolverGoogleMaps).toHaveBeenCalledWith('https://maps.app.goo.gl/short');
    expect(component.ubicacionConfirmada).toBe(true); expect(component.latitud).toBe(-15.4);
    expect(component.ubicacionPendiente?.latitud).toBe(-15.5);
    component.confirmarUbicacionPendiente(); expect(component.latitud).toBe(-15.5);
  });

  it('conserva borrador y archivos al volver del mapa y carga coordenadas solo después de confirmar', async () => {
    component.preferencias.mapaInterno = true;
    component.direccion = 'Jirón de prueba'; component.referencia = 'Puerta azul'; component.dni = '12345678';
    component.nombres = 'Nombre'; component.apellidos = 'Apellido'; component.archivos = [new File(['pdf'], 'evidencia.pdf', { type: 'application/pdf' })];
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    component.seleccionarUbicacion();
    expect(navigate).toHaveBeenCalledWith(['/mapa'], { queryParams: { retorno: 'expedientes/nuevo', q: 'Jirón de prueba' } });
    expect(JSON.parse(sessionStorage.getItem('sm_expediente_nuevo_borrador') || '{}')).toMatchObject({ direccion: 'Jirón de prueba', referencia: 'Puerta azul', dni: '12345678', nombres: 'Nombre' });
    component.ngOnDestroy();
    localStorage.setItem('sm_temp_ubicacion_mapa', JSON.stringify({ latitud: -15.4, longitud: -70.12 }));
    const returned = TestBed.createComponent(ExpedienteNuevoComponent); returned.detectChanges(); await returned.whenStable(); returned.detectChanges();
    const restored = returned.componentInstance;
    expect(restored.direccion).toBe('Jirón de prueba'); expect(restored.referencia).toBe('Puerta azul');
    expect(restored.dni).toBe('12345678'); expect(restored.archivos.map(file => file.name)).toContain('evidencia.pdf');
    restored.preferencias.mapaInterno = true;
    localStorage.setItem('sm_temp_ubicacion_mapa', JSON.stringify({ latitud: -15.4, longitud: -70.12 }));
    restored.cargarUbicacionMapa();
    expect(restored.ubicacionConfirmada).toBe(true); expect(restored.latitud).toBe(-15.4); expect(restored.longitud).toBe(-70.12);
    expect(restored.metodoUbicacion).toBe('MAPA_INTERNO');
    returned.destroy(); sessionStorage.removeItem('sm_expediente_nuevo_borrador'); localStorage.removeItem('sm_temp_ubicacion_mapa');
    navigate.mockRestore();
  });

  it('limita documentos iniciales a diez y permite retirar un archivo', () => {
    const input = document.createElement('input');
    const files = (count: number) => Array.from({ length: count }, (_, i) => new File(['x'], `doc${i}.pdf`, { type: 'application/pdf' }));
    Object.defineProperty(input, 'files', { configurable: true, value: files(11) });
    component.seleccionarArchivo({ target: input } as unknown as Event);
    expect(component.archivos).toHaveLength(0); expect(component.error).toContain('máximo 10');
    Object.defineProperty(input, 'files', { configurable: true, value: files(10) });
    component.seleccionarArchivo({ target: input } as unknown as Event);
    expect(component.archivos).toHaveLength(10);
    component.quitarArchivo(0); expect(component.archivos).toHaveLength(9);
  });
});

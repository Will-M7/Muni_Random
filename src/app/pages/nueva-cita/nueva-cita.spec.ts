import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NuevaCita } from './nueva-cita';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { VerificacionService } from '../../services/verificacion.service';
import { of } from 'rxjs';

describe('NuevaCita', () => {
  let component: NuevaCita;
  let fixture: ComponentFixture<NuevaCita>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NuevaCita], providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => null }, queryParamMap: { get: () => null } } } }, { provide: VerificacionService, useValue: { obtenerSolicitudes: async () => [], programacion$: () => of([{ codigo: 'SM-2026-00001', horaFiscalizacion: '09:30:00', ciudadano: 'Juan Pérez', estado: 'En espera' }]), guardarArchivoPendiente: () => {}, obtenerArchivoPendiente: () => null, limpiarArchivoPendiente: () => {} } }],
    }).compileComponents();

    fixture = TestBed.createComponent(NuevaCita);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('abre Google Maps con la dirección actual codificada sin salir del formulario', () => {
    const abrir = vi.spyOn(window, 'open').mockImplementation(() => null);
    component.datos.direccion = 'Jr. Unión 123, San Miguel';
    component.abrirMapa();
    expect(abrir).toHaveBeenCalledWith(
      'https://www.google.com/maps/search/?api=1&query=Jr.%20Uni%C3%B3n%20123%2C%20San%20Miguel',
      '_blank', 'noopener,noreferrer');
    abrir.mockRestore();
  });

  it('impide abrir Maps con la dirección vacía', () => {
    const abrir = vi.spyOn(window, 'open').mockImplementation(() => null);
    component.datos.direccion = '   ';
    component.abrirMapa();
    expect(abrir).not.toHaveBeenCalled();
    expect(component.mensajeError).toContain('dirección exacta');
    abrir.mockRestore();
  });

  it('restaura borrador, PDF y ubicación sin interacción adicional', async () => {
    localStorage.setItem('sm_draft_nueva_solicitud', JSON.stringify({
      dni: '12345678', nombres: 'Ana', apellidos: 'Pérez', telefono: '999999999',
      direccion: 'Calle 1', referencia: 'Parque', documento: 'sustento.pdf', documentoTamano: '12 KB',
      latitud: -15.4, longitud: -70.1, ubicacion: '-15.400000, -70.100000',
      fechaFiscalizacion: '2026-09-26', horaFiscalizacion: '10:30'
    }));
    fixture = TestBed.createComponent(NuevaCita);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#dni').value).toBe('12345678');
    expect(fixture.nativeElement.querySelector('#nombres').value).toBe('Ana');
    expect(fixture.nativeElement.textContent).toContain('sustento.pdf');
    expect(fixture.nativeElement.textContent).toContain('-15.400000, -70.100000');
    expect(fixture.nativeElement.querySelector('#fechaFiscalizacion').value).toBe('2026-09-26');
    localStorage.removeItem('sm_draft_nueva_solicitud');
  });

  it('consulta y muestra la agenda al seleccionar fecha sin clic adicional', async () => {
    component.onFechaFiscalizacionChange('2026-10-01');
    await new Promise(resolve => setTimeout(resolve, 250));
    await fixture.whenStable();
    expect(component.agendaDelDia[0].ciudadano).toBe('Juan Pérez');
    expect(fixture.nativeElement.textContent).toContain('09:30');
  });

  it('no consulta RENIEC con menos de ocho dígitos', async () => {
    component.datos.dni = '1234567';
    component.onDniInput();
    await new Promise(resolve => setTimeout(resolve, 600));
    http.expectNone(req => req.url.includes('/api/reniec/'));
  });

  it('consulta una sola vez luego del debounce y autocompleta nombres', async () => {
    component.datos.dni = '12345678';
    component.onDniInput();
    component.datos.dni = '1234567'; component.onDniInput();
    component.datos.dni = '12345678'; component.onDniInput();
    await new Promise(resolve => setTimeout(resolve, 499));
    http.expectNone(req => req.url.includes('/api/reniec/'));
    await new Promise(resolve => setTimeout(resolve, 20));
    const request = http.expectOne(req => req.url.includes('/api/reniec/12345678'));
    request.flush({ dni: '12345678', nombres: 'Ana', apellidoPaterno: 'Pérez', apellidoMaterno: 'Lima' });
    expect(component.datos.nombres).toBe('Ana');
    expect(component.datos.apellidos).toBe('Pérez Lima');
    expect(component.dniConsultadoExito).toBe(true);
  });

  it('muestra error inline si RENIEC responde 404', async () => {
    component.datos.dni = '87654321'; component.onDniInput();
    await new Promise(resolve => setTimeout(resolve, 520));
    const request = http.expectOne(req => req.url.includes('/api/reniec/87654321'));
    request.flush({ message: 'DNI no encontrado en RENIEC.' }, { status: 404, statusText: 'Not Found' });
    expect(component.dniMensaje).toContain('no encontrado');
  });

  it('permite fallback manual si RENIEC no está disponible y limpia datos al cambiar DNI', async () => {
    component.datos.dni = '87654320'; component.onDniInput();
    await new Promise(resolve => setTimeout(resolve, 520));
    const request = http.expectOne(req => req.url.includes('/api/reniec/87654320'));
    request.flush({ message: 'Consulta RENIEC no configurada.' }, { status: 503, statusText: 'Service Unavailable' });
    component.datos.nombres = 'Nombre manual';
    component.datos.apellidos = 'Apellido manual';
    expect(component.dniConsultadoExito).toBe(false);
    expect(component.dniMensaje).toContain('Complete los datos manualmente');
    component.datos.dni = '87654321'; component.onDniInput();
    expect(component.datos.nombres).toBe('');
    expect(component.datos.apellidos).toBe('');
  });
});

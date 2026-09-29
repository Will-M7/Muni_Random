import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Solicitudes } from './solicitudes';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { VerificacionService } from '../../services/verificacion.service';

describe('Solicitudes', () => {
  let component: Solicitudes;
  let fixture: ComponentFixture<Solicitudes>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Solicitudes], providers: [provideHttpClient(), provideRouter([]), { provide: VerificacionService, useValue: { obtenerSolicitudes: async () => [{ codigo: 'SM-2026-00001', nombre: 'Ana Pérez', nombres: 'Ana', apellidos: 'Pérez', dni: '12345678', telefono: '999999999', direccion: 'Calle 1', referencia: '', documento: 'sustento.pdf', ubicacion: '-15.4, -70.1', latitud: -15.4, longitud: -70.1, fecha: '2026-09-25', hora: '09:00', fechaFiscalizacion: '2026-09-26', horaFiscalizacion: '10:30', fiscalizadorId: '', fiscalizadorNombre: '', estado: 'En espera', fechaRegistro: '2026-09-25' }], obtenerSolicitudPorCodigo: () => undefined } }],
    }).compileComponents();

    fixture = TestBed.createComponent(Solicitudes);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renderiza solicitudes async sin interacción adicional', () => {
    expect(fixture.nativeElement.textContent).toContain('Ana Pérez');
    expect(fixture.nativeElement.textContent).toContain('2026-09-26');
  });
});

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { VerificacionService } from './verificacion.service';

describe('VerificacionService', () => {
  let service: VerificacionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(VerificacionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('consulta el directorio mínimo de fiscalizadores disponibles', async () => {
    const pending = service.obtenerFiscalizadores();
    const request = http.expectOne('http://localhost:8080/api/catalogos/fiscalizadores');
    expect(request.request.method).toBe('GET');
    request.flush([{ id: 'FIS-001', nombre: 'Inspector', zona: 'Centro', activo: true }]);
    await expect(pending).resolves.toEqual([{ id: 'FIS-001', nombre: 'Inspector', zona: 'Centro', activo: true }]);
  });
});

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { expect, vi } from 'vitest';
import { AuthService, Sesion } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  const sesion: Sesion = {
    token: 'token', nombre: 'Fiscalizador', cargo: 'Fiscalizador', correo: 'fiscalizador@muni.gob.pe',
    rol: 'FISCALIZADOR', fiscalizadorId: 'FIS-001',
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('envía username y password sin rol', async () => {
    const promise = service.login('fiscalizador', 'fiscal2026');
    const request = http.expectOne('http://localhost:8080/api/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ username: 'fiscalizador', password: 'fiscal2026' });
    expect(request.request.body.rol).toBeUndefined();
    request.flush(sesion);
    await expect(promise).resolves.toEqual(sesion);
  });

  it('conserva los espacios de la contraseña', async () => {
    const promise = service.login('usuario', ' clave 123 ');
    const request = http.expectOne('http://localhost:8080/api/auth/login');
    expect(request.request.body).toEqual({ username: 'usuario', password: ' clave 123 ' });
    request.flush(sesion);
    await expect(promise).resolves.toBeDefined();
  });
});

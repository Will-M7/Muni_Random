import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { LoginComponent } from './login';
import { AuthService, Sesion } from '../../services/auth.service';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authService: { isAuthenticated: ReturnType<typeof vi.fn>; login: ReturnType<typeof vi.fn>; logout: ReturnType<typeof vi.fn> };
  let router: Router;
  const session = (rol: 'ADMIN_SISTEMA' | 'FISCALIZADOR' | 'MUNICIPIO'): Sesion => ({
    token: 'token', nombre: 'Usuario', cargo: 'Cargo', correo: 'usuario@muni.gob.pe', rol,
    capacidades: rol === 'ADMIN_SISTEMA' ? ['ROLES_VER'] : rol === 'FISCALIZADOR' ? ['TAREAS_PROPIAS_VER'] : ['SOLICITUD_VER'],
  });

  beforeEach(async () => {
    authService = {
      isAuthenticated: vi.fn().mockReturnValue(false),
      login: vi.fn(),
      logout: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: AuthService, useValue: authService }],
    }).compileComponents();
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    component.ngOnInit();
  });

  it('redirige a /perfil con la respuesta FISCALIZADOR', async () => {
    authService.login.mockResolvedValue(session('FISCALIZADOR'));
    component.usuario = 'fiscalizador';
    component.password = 'fiscal2026';
    component.iniciarSesion();
    await fixture.whenStable();
    expect(authService.login).toHaveBeenCalledWith('fiscalizador', 'fiscal2026');
    expect(router.navigate).toHaveBeenCalledWith(['/fiscalizador']);
  });

  it('redirige a /inicio con la respuesta MUNICIPIO', async () => {
    authService.login.mockResolvedValue(session('MUNICIPIO'));
    component.usuario = 'municipio';
    component.password = 'municipio2026';
    component.iniciarSesion();
    await fixture.whenStable();
    expect(router.navigate).toHaveBeenCalledWith(['/inicio']);
  });

  it('redirige a /admin con capacidades de administración', async () => {
    authService.login.mockResolvedValue({ ...session('ADMIN_SISTEMA'), capacidades: ['ROLES_VER', 'USUARIOS_VER'] });
    component.usuario = 'admin';
    component.password = 'clave-segura';
    component.iniciarSesion();
    await fixture.whenStable();
    expect(router.navigate).toHaveBeenCalledWith(['/admin']);
  });

  it('conserva los espacios de la contraseña', async () => {
    authService.login.mockResolvedValue(session('MUNICIPIO'));
    component.usuario = 'usuario';
    component.password = ' clave 123 ';
    component.iniciarSesion();
    await fixture.whenStable();
    expect(authService.login).toHaveBeenCalledWith('usuario', ' clave 123 ');
  });

  it('muestra el mensaje de credenciales para 401', async () => {
    authService.login.mockRejectedValue(new HttpErrorResponse({ status: 401 }));
    component.usuario = 'fiscalizador';
    component.password = 'incorrecta';
    component.iniciarSesion();
    await fixture.whenStable();
    expect(component.mensajeError).toBe('Usuario o contraseña incorrectos.');
  });

  it('muestra un mensaje distinto para 500', async () => {
    authService.login.mockRejectedValue(new HttpErrorResponse({ status: 500 }));
    component.usuario = 'fiscalizador';
    component.password = 'fiscal2026';
    component.iniciarSesion();
    await fixture.whenStable();
    expect(component.mensajeError).toBe('El servidor no pudo completar el inicio de sesión.');
    expect(component.mensajeError).not.toContain('contraseña incorrectos');
  });
});

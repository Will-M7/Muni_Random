import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { UsuariosComponent } from './usuarios';
import { UsuariosService } from '../../services/usuarios.service';

describe('Usuarios', () => {
  let fixture: ComponentFixture<UsuariosComponent>;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UsuariosComponent],
      providers: [{ provide: UsuariosService, useValue: { listar: () => Promise.resolve([{ username: 'municipio', nombre: 'Municipio', rol: 'MUNICIPIO', activo: true, fechaCreacion: '2026-09-25' }]), crear: () => of({}) } }]
    }).compileComponents();
    fixture = TestBed.createComponent(UsuariosComponent);
    await fixture.whenStable();
  });
  it('renderiza usuarios sin interacción adicional', () => { expect(fixture.nativeElement.textContent).toContain('municipio'); });
});

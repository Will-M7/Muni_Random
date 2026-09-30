import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Perfil } from './perfil';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../services/auth.service';

describe('Perfil', () => {
  let component: Perfil;
  let fixture: ComponentFixture<Perfil>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Perfil], providers: [provideRouter([]), { provide: AuthService, useValue: { session: null, homeRoute: () => '/perfil' } }],
    }).compileComponents();

    fixture = TestBed.createComponent(Perfil);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

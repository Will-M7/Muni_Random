import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Predial } from './predial';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

describe('Predial', () => {
  let component: Predial;
  let fixture: ComponentFixture<Predial>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Predial], providers: [provideHttpClient(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Predial);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

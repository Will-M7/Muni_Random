import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Mapa, normalizedGeocodeQuery, parseCoordinates } from './mapa';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';

describe('Mapa', () => {
  let component: Mapa;
  let fixture: ComponentFixture<Mapa>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Mapa], providers: [provideHttpClient(), provideRouter([]), { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: { get: () => null } } } }],
    }).compileComponents();

    fixture = TestBed.createComponent(Mapa);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('detecta coordenadas decimales con coma o espacio y rechaza rangos inválidos', () => {
    expect(parseCoordinates('-15.48, -70.13')).toEqual([-15.48, -70.13]);
    expect(parseCoordinates('-15.488624 -70.130969')).toEqual([-15.488624, -70.130969]);
    expect(parseCoordinates('91, -70')).toBeNull();
    expect(parseCoordinates('una dirección')).toBeNull();
  });

  it('convierte grados, minutos y segundos con hemisferios sur y oeste', () => {
    const result = parseCoordinates('15°25\'40.5"S 70°08\'16.1"W');
    expect(result?.[0]).toBeCloseTo(-15.4279167, 6);
    expect(result?.[1]).toBeCloseTo(-70.1378056, 6);
    expect(parseCoordinates('95°25\'40"S 70°08\'16"W')).toBeNull();
  });

  it('normaliza abreviaciones y agrega el contexto geográfico local', () => {
    expect(normalizedGeocodeQuery('  JR.   Tacná ')).toBe('jiron tacna, San Miguel, Juliaca, Puno, Perú');
    expect(normalizedGeocodeQuery('av libertad, Puno')).toBe('avenida libertad, puno');
  });

  it('consulta sugerencias solo al enviar la búsqueda y descarta coordenadas de respuesta inválidas', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => [
      { display_name: 'Jirón Tacna, San Miguel, Perú', lat: '-15.4', lon: '-70.12' },
      { display_name: 'Respuesta inválida', lat: '120', lon: '500' },
    ] });
    vi.stubGlobal('fetch', fetchMock);
    component.terminoBusqueda = 'jr tacna';
    await component.buscarUbicacion();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const requested = new URL(fetchMock.mock.calls[0][0] as string);
    expect(requested.searchParams.get('q')).toContain('jiron tacna, San Miguel, Juliaca, Puno');
    expect(requested.searchParams.get('bounded')).toBe('0');
    expect(component.resultadosBusqueda).toHaveLength(1);
    vi.unstubAllGlobals();
  });
});

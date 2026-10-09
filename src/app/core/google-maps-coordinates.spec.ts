import { describe, expect, it } from 'vitest';
import { extractGoogleMapsCoordinates } from './google-maps-coordinates';

describe('coordenadas de enlaces compartidos de Google Maps', () => {
  it('acepta enlaces de pin, centro y query', () => {
    expect(extractGoogleMapsCoordinates('https://www.google.com/maps/place/x/data=!3d-15.4!4d-70.1')).toEqual([-15.4, -70.1]);
    expect(extractGoogleMapsCoordinates('https://www.google.com/maps/@-15.4,-70.1,17z')).toEqual([-15.4, -70.1]);
    expect(extractGoogleMapsCoordinates('https://maps.google.com/?q=-15.4,-70.1')).toEqual([-15.4, -70.1]);
  });
  it('rechaza enlaces cortos sin coordenadas, dominios ajenos y rangos inválidos', () => {
    expect(extractGoogleMapsCoordinates('https://maps.app.goo.gl/abc')).toBeNull();
    expect(extractGoogleMapsCoordinates('https://google.com.evil.test/?q=-15,-70')).toBeNull();
    expect(extractGoogleMapsCoordinates('https://www.google.com/maps/@91,-70,17z')).toBeNull();
  });
});

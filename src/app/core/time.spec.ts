import { describe, expect, it } from 'vitest';
import { formatTime12, formatTimeDual, validTime24 } from './time';

describe('formato horario municipal', () => {
  it('acepta los límites de 24 horas', () => {
    expect(validTime24('00:00')).toBe(true);
    expect(validTime24('23:59')).toBe(true);
    expect(validTime24('24:00')).toBe(false);
  });

  it('muestra equivalencias de 12 horas sin perder HH:mm', () => {
    expect(formatTime12('01:00')).toBe('1:00 a. m.');
    expect(formatTime12('13:00')).toBe('1:00 p. m.');
    expect(formatTime12('16:00')).toBe('4:00 p. m.');
    expect(formatTimeDual('23:59')).toBe('23:59 (11:59 p. m.)');
  });
});

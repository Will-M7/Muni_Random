import { describe, expect, it } from 'vitest';
import { formatLimaLocalDateTime } from './date';

describe('fecha de carga almacenada en America/Lima',()=>{
  it('muestra la hora local guardada sin convertirla según el navegador',()=>{
    expect(formatLimaLocalDateTime('2026-10-08T23:59:12')).toBe('08/10/2026 23:59');
    expect(formatLimaLocalDateTime(null)).toBe('—');
  });
});

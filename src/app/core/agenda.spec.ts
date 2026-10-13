import {describe,it,expect} from 'vitest';
import {occupancyColor,occupiesCapacity} from './agenda';
import {visitKey,visitColor,visitPin,VISIT_COLORS} from './visit-identity';
describe('ocupación de agenda',()=>{
  it('colorea 0 blanco, 1–3 verde, 4–5 amarillo y 6 rojo con límite 5',()=>{expect([0,1,2,3,4,5,6].map(n=>occupancyColor(n,5))).toEqual(['neutral','green','green','green','yellow','yellow','red']);});
  it('respeta límites 1, 2 y 7 sin redondear la capacidad a cero',()=>{expect([0,1,2].map(n=>occupancyColor(n,1))).toEqual(['neutral','yellow','red']);expect(occupancyColor(1,2)).toBe('green');expect(occupancyColor(5,7)).toBe('green');expect(occupancyColor(6,7)).toBe('yellow');expect(occupancyColor(8,7)).toBe('red');expect(occupancyColor(2,0)).toBe('neutral');});
  it('no cuenta visitas canceladas, sustituidas o concluidas',()=>{expect(['PROGRAMADA','EN_CURSO','VENCIDA'].every(occupiesCapacity)).toBe(true);expect(['CANCELADA','REPROGRAMADA','REALIZADA','NO_REALIZADA'].some(occupiesCapacity)).toBe(false);});
});
describe('identificación de visitas',()=>{
  it('usa una paleta determinista y el mismo número y color en SVG',()=>{expect([1,2,3,4].map(visitColor)).toEqual(VISIT_COLORS.slice(0,4));expect(visitColor(9)).toBe(visitColor(1));expect(visitPin(2,visitColor(2))).toContain('fill="#15803d"');expect(visitPin(2,visitColor(2))).toContain('>2</text>');});
  it('distingue varias visitas del mismo expediente por programación',()=>{expect(visitKey({codigo:'FIS-1',hora:'09:00',programacionId:1})).not.toBe(visitKey({codigo:'FIS-1',hora:'09:00',programacionId:2}));});
});

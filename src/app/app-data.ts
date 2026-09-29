import { Solicitud, Rol, EstadoSolicitud, Fiscalizador } from './models/solicitud.model';
export type { Rol, EstadoSolicitud, Fiscalizador, Solicitud };
// Compatibilidad para imports heredados; la fuente real de datos es la API REST.
export const SOLICITUD_DEMO: Solicitud | undefined = undefined;
export function solicitudActual(): Solicitud | undefined { return undefined; }

export type Rol = 'ADMIN_SISTEMA' | 'MUNICIPIO' | 'FISCALIZADOR';

export type EstadoSolicitud =
  | 'En espera'
  | 'Verificado'
  | 'Observada'
  | 'Expirado';
export type ResultadoFiscalizacion = 'PENDIENTE' | 'REALIZADA' | 'NO_REALIZADA';

export interface Fiscalizador {
  id: string;
  nombre: string;
  zona: string;
  activo: boolean;
}

export interface Solicitud {
  codigo: string;
  nombres: string;
  apellidos: string;
  nombre: string; // Nombre completo (computado o concatenado)
  dni: string;
  telefono: string;
  direccion: string;
  referencia: string;
  documento: string;
  documentoTamano?: string;
  ubicacion: string; // Formato "latitud, longitud"
  latitud: number;
  longitud: number;
  fecha: string; // YYYY-MM-DD
  hora: string; // Ej: "09:00 a. m."
  fechaFiscalizacion?: string;
  horaFiscalizacion?: string;
  fiscalizadorId: string;
  fiscalizadorNombre: string;
  estado: EstadoSolicitud;
  observaciones?: string;
  resultadoVisita?: string;
  resultadoFiscalizacion?: ResultadoFiscalizacion;
  observacionesFiscalizador?: string;
  reporteNombreOriginal?: string;
  reporteTamano?: number;
  fechaEjecucion?: string;
  fechaRegistro: string;
  historialCambios?: { fecha: string; estado: EstadoSolicitud; nota?: string }[];
}

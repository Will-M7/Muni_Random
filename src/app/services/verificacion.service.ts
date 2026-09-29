import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom, Observable } from 'rxjs';
import { EstadoSolicitud, Solicitud, Fiscalizador, ResultadoFiscalizacion } from '../models/solicitud.model';
import { environment } from '../../environments/environment';

export const MUNICIPALIDAD_SAN_MIGUEL_COORDS: [number, number] = [-15.478348, -70.124702];
interface SolicitudPayload extends Partial<Solicitud> {}
export interface ProgramacionDia { codigo: string; horaFiscalizacion: string; ciudadano: string; fiscalizador: string; estado: string; }
export interface FiscalizadorTarea { codigo:string; ciudadano:string; dni:string; telefono:string; direccion:string; referencia:string; fecha:string; hora:string; latitud:number; longitud:number; documento:string; resultado:'PENDIENTE'|'REALIZADA'|'NO_REALIZADA'; observaciones?:string; observacionesFiscalizador?:string; observacionesMunicipales?:string; reporteNombreOriginal?:string; reporteTamano?:number; fechaEjecucion?:string; estadoSolicitud:string; }

@Injectable({ providedIn: 'root' })
export class VerificacionService {
  private cache: Solicitud[] = [];
  private archivoPendiente: File | null = null;
  private readonly base = `${environment.apiUrl}/api`;
  constructor(private http: HttpClient) {}
  guardarArchivoPendiente(file: File | null): void { this.archivoPendiente = file; }
  obtenerArchivoPendiente(): File | null { return this.archivoPendiente; }
  limpiarArchivoPendiente(): void { this.archivoPendiente = null; }
  async obtenerSolicitudes(params?: { q?: string; estado?: string; fecha?: string; fiscalizadorId?: string }): Promise<Solicitud[]> { let p=new HttpParams();Object.entries(params??{}).forEach(([k,v])=>{if(v)p=p.set(k,v);});this.cache=await firstValueFrom(this.http.get<Solicitud[]>(`${this.base}/solicitudes`,{params:p}));return this.cache; }
  programacion$(fecha: string): Observable<ProgramacionDia[]> { return this.http.get<ProgramacionDia[]>(`${this.base}/solicitudes/programacion`, { params: { fecha } }); }
  async obtenerFiscalizadores(): Promise<Fiscalizador[]> { return firstValueFrom(this.http.get<Fiscalizador[]>(`${this.base}/usuarios/fiscalizadores`)); }
  async obtenerMisTareas(fecha?: string): Promise<FiscalizadorTarea[]> { let p=new HttpParams(); if(fecha)p=p.set('fecha',fecha); return firstValueFrom(this.http.get<FiscalizadorTarea[]>(`${this.base}/fiscalizador/mis-tareas`,{params:p})); }
  async obtenerMisProximas(): Promise<FiscalizadorTarea[]> { return firstValueFrom(this.http.get<FiscalizadorTarea[]>(`${this.base}/fiscalizador/mis-tareas/proximas`)); }
  async obtenerMiHistorial(fecha?: string, resultado?: string): Promise<FiscalizadorTarea[]> { let params = new HttpParams(); if (fecha) params = params.set('fecha', fecha); if (resultado && resultado !== 'TODOS') params = params.set('resultado', resultado); return firstValueFrom(this.http.get<FiscalizadorTarea[]>(`${this.base}/fiscalizador/mis-tareas/historial`, { params })); }
  async obtenerMiTarea(codigo:string): Promise<Solicitud> { return firstValueFrom(this.http.get<Solicitud>(`${this.base}/fiscalizador/mis-tareas/${encodeURIComponent(codigo)}`)); }
  async registrarResultado(codigo:string, resultado:ResultadoFiscalizacion, observaciones:string, reporte:File|null): Promise<Solicitud> { const form=new FormData(); form.append('resultado',new Blob([JSON.stringify({resultado,observaciones})],{type:'application/json'})); if(reporte)form.append('reporte',reporte,reporte.name); return firstValueFrom(this.http.put<Solicitud>(`${this.base}/fiscalizador/mis-tareas/${encodeURIComponent(codigo)}/resultado`,form)); }
  async abrirReporte(codigo:string): Promise<Blob> { return firstValueFrom(this.http.get(`${this.base}/solicitudes/${encodeURIComponent(codigo)}/reporte`,{responseType:'blob'})); }
  async abrirDocumentoTarea(codigo:string): Promise<Blob> { return firstValueFrom(this.http.get(`${this.base}/fiscalizador/mis-tareas/${encodeURIComponent(codigo)}/documento`,{responseType:'blob'})); }
  async abrirReporteTarea(codigo:string): Promise<Blob> { return firstValueFrom(this.http.get(`${this.base}/fiscalizador/mis-tareas/${encodeURIComponent(codigo)}/reporte`,{responseType:'blob'})); }
  obtenerSolicitudPorCodigo(codigo: string): Solicitud|undefined { return this.cache.find(s=>s.codigo===codigo); }
  async cargarSolicitud(codigo: string): Promise<Solicitud> { const s=await firstValueFrom(this.http.get<Solicitud>(`${this.base}/solicitudes/${codigo}`));this.cache=[s,...this.cache.filter(x=>x.codigo!==codigo)];return s; }
  async guardarSolicitud(datos: SolicitudPayload, archivo?: File | null): Promise<Solicitud> { const body={dni:datos.dni,nombres:datos.nombres,apellidos:datos.apellidos,telefono:datos.telefono,direccion:datos.direccion,referencia:datos.referencia??'',documento:datos.documento??'',documentoTamano:datos.documentoTamano??'',latitud:datos.latitud,longitud:datos.longitud,fecha:datos.fecha,hora:datos.hora,fechaFiscalizacion:datos.fechaFiscalizacion||null,horaFiscalizacion:datos.horaFiscalizacion||null,fiscalizadorId:datos.fiscalizadorId??'',estado:datos.estado,observaciones:datos.observaciones??''}; const form=new FormData(); form.append('solicitud', new Blob([JSON.stringify(body)], {type:'application/json'})); if (archivo) form.append('documento', archivo, archivo.name); const url=datos.codigo?`${this.base}/solicitudes/${datos.codigo}`:`${this.base}/solicitudes`; const request=datos.codigo?this.http.put<Solicitud>(url,form):this.http.post<Solicitud>(url,form);const result=await firstValueFrom(request);this.cache=[result,...this.cache.filter(x=>x.codigo!==result.codigo)];return result; }
  async cambiarEstado(codigo:string,estado:EstadoSolicitud|string,resultado?:string,observacionAdicional?:string):Promise<boolean>{await firstValueFrom(this.http.patch(`${this.base}/solicitudes/${codigo}/estado`,{estado,resultado,observacionAdicional}));await this.obtenerSolicitudes();return true;}
  async abrirDocumento(codigo: string): Promise<Blob> { return firstValueFrom(this.http.get(`${this.base}/solicitudes/${encodeURIComponent(codigo)}/documento`, { responseType: 'blob' })); }
  calcularDistanciaKm(o:[number,number],d:[number,number]):number{const r=Math.PI/180,a=Math.sin((d[0]-o[0])*r/2)**2+Math.cos(o[0]*r)*Math.cos(d[0]*r)*Math.sin((d[1]-o[1])*r/2)**2;return 6371*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));}
  extraerCoordenadas(s:Solicitud):[number,number]{return[s.latitud||MUNICIPALIDAD_SAN_MIGUEL_COORDS[0],s.longitud||MUNICIPALIDAD_SAN_MIGUEL_COORDS[1]];}
  calcularMejorRuta(origen:[number,number],visitas:Solicitud[]):{orden:Solicitud[];distanciaTotalKm:number;googleMapsUrl:string}{const p=[...visitas],orden:Solicitud[]=[];let actual=origen,dist=0;while(p.length){p.sort((a,b)=>this.calcularDistanciaKm(actual,this.extraerCoordenadas(a))-this.calcularDistanciaKm(actual,this.extraerCoordenadas(b)));const n=p.shift()!;dist+=this.calcularDistanciaKm(actual,this.extraerCoordenadas(n));orden.push(n);actual=this.extraerCoordenadas(n);}return{orden,distanciaTotalKm:+dist.toFixed(2),googleMapsUrl:this.construirGoogleMapsRuta(origen,orden)};}
  construirGoogleMapsRuta(o:[number,number],orden:Solicitud[]):string{if(!orden.length)return '';const origin=`${o[0]},${o[1]}`,d=orden.map(v=>{const c=this.extraerCoordenadas(v);return`${c[0]},${c[1]}`}),dest=d.at(-1)!;const w=d.slice(0,-1).join('|');return`https://www.google.com/maps/dir/?api=1&origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(dest)}${w?`&waypoints=${encodeURIComponent(w)}`:''}&travelmode=driving`;}
  construirGoogleMapsDestinoUnico(s:Solicitud):string{const c=this.extraerCoordenadas(s);return`https://www.google.com/maps/search/?api=1&query=${c[0]},${c[1]}`;}
}

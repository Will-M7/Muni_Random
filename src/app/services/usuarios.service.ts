import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

export type RolUsuario = 'ADMIN_SISTEMA' | 'MUNICIPIO' | 'FISCALIZADOR';
export interface Usuario { username: string; nombre: string; rol: RolUsuario; roles: RolUsuario[]; activo: boolean; fechaCreacion: string; fiscalizadorId:string|null; fiscalizadorNombre:string|null; }
export interface UsuarioNuevo { username: string; nombre: string; password: string; confirmarPassword?:string; rol: RolUsuario; fiscalizadorId?:string; }
export interface UserPreferences { mapaInterno:boolean; googleExterno:boolean; googleUrl:boolean; descargarJornadaHtml:boolean; }
export interface IdentityConfiguration { provider:'DISABLED'|'RENIEC'|'PERUAPI_COM'; endpoint:string|null; credentialConfigured:boolean; actualizadoPor:string|null; actualizadoEn:string|null; }
export interface FiscalizadorSinCuenta { id:string; nombre:string; zona:string; activo:boolean; }
export interface RolSistema { nombre: RolUsuario; descripcion: string; configurable: boolean; capacidades: string[]; }
export interface Capacidad { codigo: string; descripcion: string; categoria: string; }

@Injectable({ providedIn: 'root' })
export class UsuariosService {
  private readonly url = `${environment.apiUrl}/api/usuarios`;
  constructor(private http: HttpClient) {}
  listar(): Promise<Usuario[]> { return firstValueFrom(this.http.get<Usuario[]>(this.url)); }
  crear(data: UsuarioNuevo): Promise<Usuario> { return firstValueFrom(this.http.post<Usuario>(this.url, data)); }
  cambiarEstado(username: string, activo: boolean): Promise<Usuario> { return firstValueFrom(this.http.patch<Usuario>(`${this.url}/${encodeURIComponent(username)}/estado`, { activo })); }
  asignarRoles(username: string, roles: RolUsuario[],fiscalizadorId?:string): Promise<Usuario> { return firstValueFrom(this.http.put<Usuario>(`${this.url}/${encodeURIComponent(username)}/roles`, { roles,fiscalizadorId:fiscalizadorId||null })); }
  fiscalizadoresSinCuenta():Promise<FiscalizadorSinCuenta[]>{return firstValueFrom(this.http.get<FiscalizadorSinCuenta[]>(`${this.url}/fiscalizadores-sin-cuenta`));}
  listarRoles(): Promise<RolSistema[]> { return firstValueFrom(this.http.get<RolSistema[]>(`${environment.apiUrl}/api/admin/roles`)); }
  listarCapacidades(): Promise<Capacidad[]> { return firstValueFrom(this.http.get<Capacidad[]>(`${environment.apiUrl}/api/admin/capacidades`)); }
  guardarCapacidades(nombre: string, capacidades: string[]): Promise<RolSistema> { return firstValueFrom(this.http.put<RolSistema>(`${environment.apiUrl}/api/admin/roles/${encodeURIComponent(nombre)}/capacidades`, { capacidades })); }
  preferencias(username:string):Promise<UserPreferences>{return firstValueFrom(this.http.get<UserPreferences>(`${environment.apiUrl}/api/settings/users/${encodeURIComponent(username)}`));}
  guardarPreferencias(username:string,value:UserPreferences):Promise<UserPreferences>{return firstValueFrom(this.http.put<UserPreferences>(`${environment.apiUrl}/api/settings/users/${encodeURIComponent(username)}`,value));}
  misPreferencias():Promise<UserPreferences>{return firstValueFrom(this.http.get<UserPreferences>(`${environment.apiUrl}/api/settings/me`));}
  limiteDiario():Promise<number>{return firstValueFrom(this.http.get<{limite:number}>(`${environment.apiUrl}/api/settings/daily-limit`)).then(x=>x.limite);}
  guardarLimite(limite:number):Promise<number>{return firstValueFrom(this.http.put<{limite:number}>(`${environment.apiUrl}/api/settings/daily-limit`,{limite})).then(x=>x.limite);}
  integracion():Promise<IdentityConfiguration>{return firstValueFrom(this.http.get<IdentityConfiguration>(`${environment.apiUrl}/api/identity-integration`));}
  guardarIntegracion(value:{provider:string;endpoint:string|null;apiKey:string|null;clientId:string|null;clientSecret:string|null}):Promise<IdentityConfiguration>{return firstValueFrom(this.http.put<IdentityConfiguration>(`${environment.apiUrl}/api/identity-integration`,value));}
  probarIntegracion():Promise<{message:string}>{return firstValueFrom(this.http.post<{message:string}>(`${environment.apiUrl}/api/identity-integration/test`,{}));}
  identidadDisponible():Promise<boolean>{return firstValueFrom(this.http.get<{enabled:boolean}>(`${environment.apiUrl}/api/identity-integration/availability`)).then(x=>x.enabled);}
}

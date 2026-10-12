import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

export type RolUsuario = 'ADMIN_SISTEMA' | 'MUNICIPIO' | 'FISCALIZADOR';
export interface Usuario { username: string; nombre: string; rol: RolUsuario; roles: RolUsuario[]; activo: boolean; archivado:boolean; fechaCreacion: string; fiscalizadorId:string|null; fiscalizadorNombre:string|null; }
export interface UsuarioEliminacion { username:string; accion:'ELIMINADA'|'ARCHIVADA'|'ERROR'; referencias:string[]; mensaje:string; }
export interface UsuarioEliminacionPreview { username:string; nombre:string; puedeEliminar:boolean; referencias:string[]; }
export interface UsuarioNuevo { username: string; nombre: string; password: string; confirmarPassword?:string; rol: RolUsuario; fiscalizadorId?:string; }
export interface UserPreferences { mapaInterno:boolean; googleExterno:boolean; googleUrl:boolean; descargarJornadaHtml:boolean; }
export interface IdentityConfiguration { provider:'DISABLED'|'RENIEC'|'PERUAPI_COM'|'FACTILIZA'; endpoint:string|null; credentialConfigured:boolean; actualizadoPor:string|null; actualizadoEn:string|null; }
export interface IdentityDniTestResult { dni:string; nombres:string; apellidoPaterno:string; apellidoMaterno:string; provider:'RENIEC'|'PERUAPI_COM'|'FACTILIZA'; message:string; }
export interface IdentityDniQuota { applies:boolean; provider:string; date:string; used:number; limit:number; remaining:number; exhausted:boolean; resetsAt:string; zone:string; }
export interface FiscalizadorSinCuenta { id:string; nombre:string; zona:string; activo:boolean; }
export interface RolSistema { nombre: RolUsuario; descripcion: string; configurable: boolean; capacidades: string[]; }
export interface Capacidad { codigo: string; descripcion: string; categoria: string; }

@Injectable({ providedIn: 'root' })
export class UsuariosService {
  private readonly url = `${environment.apiUrl}/api/usuarios`;
  constructor(private http: HttpClient) {}
  listar(filtro='activos'): Promise<Usuario[]> { return firstValueFrom(this.http.get<Usuario[]>(this.url,{params:{filtro}})); }
  crear(data: UsuarioNuevo): Promise<Usuario> { return firstValueFrom(this.http.post<Usuario>(this.url, data)); }
  cambiarEstado(username: string, activo: boolean): Promise<Usuario> { return firstValueFrom(this.http.patch<Usuario>(`${this.url}/${encodeURIComponent(username)}/estado`, { activo })); }
  editar(username:string,value:{username:string;nombre:string;password:string|null;rol:RolUsuario}):Promise<Usuario>{return firstValueFrom(this.http.put<Usuario>(`${this.url}/${encodeURIComponent(username)}/perfil`,value));}
  previsualizarEliminacion(username:string):Promise<UsuarioEliminacionPreview>{return firstValueFrom(this.http.get<UsuarioEliminacionPreview>(`${this.url}/${encodeURIComponent(username)}/eliminacion`));}
  eliminar(username:string):Promise<UsuarioEliminacion>{return firstValueFrom(this.http.delete<UsuarioEliminacion>(`${this.url}/cuentas/${encodeURIComponent(username)}`));}
  eliminarLote(usernames:string[]):Promise<{resultados:UsuarioEliminacion[]}>{return firstValueFrom(this.http.post<{resultados:UsuarioEliminacion[]}>(`${this.url}/eliminacion-lote`,{usernames}));}
  archivar(username:string,archivado:boolean):Promise<Usuario>{return firstValueFrom(this.http.patch<Usuario>(`${this.url}/${encodeURIComponent(username)}/archivo`,{archivado}));}
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
  probarDni(dni:string):Promise<IdentityDniTestResult>{return firstValueFrom(this.http.post<IdentityDniTestResult>(`${environment.apiUrl}/api/identity-integration/test-dni`,{dni}));}
  cuotaDni():Promise<IdentityDniQuota>{return firstValueFrom(this.http.get<IdentityDniQuota>(`${environment.apiUrl}/api/identity-integration/quota`));}
  identidadDisponible():Promise<boolean>{return firstValueFrom(this.http.get<{enabled:boolean}>(`${environment.apiUrl}/api/identity-integration/availability`)).then(x=>x.enabled);}
}

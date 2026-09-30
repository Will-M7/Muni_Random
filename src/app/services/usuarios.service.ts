import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

export type RolUsuario = 'ADMIN_SISTEMA' | 'MUNICIPIO' | 'FISCALIZADOR';
export interface Usuario { username: string; nombre: string; rol: RolUsuario; roles: RolUsuario[]; activo: boolean; fechaCreacion: string; }
export interface UsuarioNuevo { username: string; nombre: string; password: string; confirmarPassword: string; rol: RolUsuario; }
export interface RolSistema { nombre: RolUsuario; descripcion: string; configurable: boolean; capacidades: string[]; }
export interface Capacidad { codigo: string; descripcion: string; categoria: string; }

@Injectable({ providedIn: 'root' })
export class UsuariosService {
  private readonly url = `${environment.apiUrl}/api/usuarios`;
  constructor(private http: HttpClient) {}
  listar(): Promise<Usuario[]> { return firstValueFrom(this.http.get<Usuario[]>(this.url)); }
  crear(data: UsuarioNuevo): Promise<Usuario> { return firstValueFrom(this.http.post<Usuario>(this.url, data)); }
  cambiarEstado(username: string, activo: boolean): Promise<Usuario> { return firstValueFrom(this.http.patch<Usuario>(`${this.url}/${encodeURIComponent(username)}/estado`, { activo })); }
  asignarRoles(username: string, roles: RolUsuario[]): Promise<Usuario> { return firstValueFrom(this.http.put<Usuario>(`${this.url}/${encodeURIComponent(username)}/roles`, { roles })); }
  listarRoles(): Promise<RolSistema[]> { return firstValueFrom(this.http.get<RolSistema[]>(`${environment.apiUrl}/api/admin/roles`)); }
  listarCapacidades(): Promise<Capacidad[]> { return firstValueFrom(this.http.get<Capacidad[]>(`${environment.apiUrl}/api/admin/capacidades`)); }
  guardarCapacidades(nombre: string, capacidades: string[]): Promise<RolSistema> { return firstValueFrom(this.http.put<RolSistema>(`${environment.apiUrl}/api/admin/roles/${encodeURIComponent(nombre)}/capacidades`, { capacidades })); }
}

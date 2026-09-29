import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

export type RolUsuario = 'MUNICIPIO' | 'FISCALIZADOR';
export interface Usuario { username: string; nombre: string; rol: RolUsuario; activo: boolean; fechaCreacion: string; }
export interface UsuarioNuevo { username: string; nombre: string; password: string; confirmarPassword: string; rol: RolUsuario; }

@Injectable({ providedIn: 'root' })
export class UsuariosService {
  private readonly url = `${environment.apiUrl}/api/usuarios`;
  constructor(private http: HttpClient) {}
  listar(): Promise<Usuario[]> { return firstValueFrom(this.http.get<Usuario[]>(this.url)); }
  crear(data: UsuarioNuevo): Promise<Usuario> { return firstValueFrom(this.http.post<Usuario>(this.url, data)); }
}

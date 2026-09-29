import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RolUsuario, Usuario, UsuariosService } from '../../services/usuarios.service';

@Component({ selector: 'app-usuarios', imports: [CommonModule, FormsModule], templateUrl: './usuarios.html', styleUrl: './usuarios.css' })
export class UsuariosComponent implements OnInit {
  usuarios: Usuario[] = []; cargando = false; guardando = false; mensaje = ''; error = '';
  nuevo = { nombre: '', username: '', password: '', confirmarPassword: '', rol: 'FISCALIZADOR' as RolUsuario };
  constructor(private service: UsuariosService, private changeDetector: ChangeDetectorRef) {}
  async ngOnInit(): Promise<void> { await this.cargar(); }
  async cargar(): Promise<void> { this.cargando = true; this.error = ''; try { this.usuarios = [...await this.service.listar()]; } catch { this.error = 'No se pudo cargar la lista de usuarios.'; } finally { this.cargando = false; this.changeDetector.markForCheck(); } }
  async crear(): Promise<void> { this.error = ''; this.mensaje = ''; if (this.nuevo.password !== this.nuevo.confirmarPassword) { this.error = 'Las contraseñas no coinciden.'; return; } this.guardando = true; try { await this.service.crear(this.nuevo); this.mensaje = 'Usuario creado correctamente.'; this.nuevo = { nombre: '', username: '', password: '', confirmarPassword: '', rol: 'FISCALIZADOR' }; await this.cargar(); } catch (e) { this.error = e instanceof HttpErrorResponse && e.status === 409 ? 'El nombre de usuario ya existe.' : 'No se pudo crear el usuario.'; } finally { this.guardando = false; this.changeDetector.markForCheck(); } }
}

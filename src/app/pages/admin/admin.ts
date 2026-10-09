import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Capacidad, FiscalizadorSinCuenta, RolSistema, RolUsuario, Usuario, UsuarioNuevo, UsuariosService } from '../../services/usuarios.service';

@Component({ selector: 'app-admin', imports: [CommonModule, FormsModule], templateUrl: './admin.html', styleUrl: './admin.css' })
export class AdminComponent implements OnInit {
  seccion: 'usuarios' | 'roles' = 'usuarios';
  usuarios: Usuario[] = [];
  fiscalizadoresSinCuenta:FiscalizadorSinCuenta[]=[];
  roles: RolSistema[] = [];
  capacidades: Capacidad[] = [];
  rolSeleccionado = '';
  nuevo: UsuarioNuevo = { nombre: '', username: '', password: '', confirmarPassword: '', rol: 'MUNICIPIO', fiscalizadorId:'' };
  cargando = false;
  guardando = false;
  mensaje = '';
  error = '';
  rolesEditados:Record<string,RolUsuario[]>={};

  constructor(private service: UsuariosService, private cdr: ChangeDetectorRef) {}
  async ngOnInit(): Promise<void> { await Promise.all([this.cargarUsuarios(), this.cargarRoles(),this.cargarFiscalizadoresSinCuenta()]); }
  async cargarFiscalizadoresSinCuenta():Promise<void>{try{this.fiscalizadoresSinCuenta=await this.service.fiscalizadoresSinCuenta();}catch{this.fiscalizadoresSinCuenta=[];}this.cdr.markForCheck();}
  get rolActual(): RolSistema | undefined { return this.roles.find(role => role.nombre === this.rolSeleccionado); }
  capacidadesPorCategoria(): [string, Capacidad[]][] {
    const groups = new Map<string, Capacidad[]>();
    for (const capability of this.capacidades) groups.set(capability.categoria, [...(groups.get(capability.categoria) ?? []), capability]);
    return [...groups.entries()];
  }
  async cargarUsuarios(): Promise<void> { this.cargando = true; this.error = ''; try { this.usuarios = await this.service.listar();this.rolesEditados=Object.fromEntries(this.usuarios.map(u=>[u.username,[...u.roles]])); } catch { this.error = 'No se pudo cargar la lista de usuarios.'; } finally { this.cargando = false; this.cdr.markForCheck(); } }
  actualizarRoles(username:string,select:HTMLSelectElement){this.rolesEditados[username]=Array.from(select.selectedOptions).map(o=>o.value as RolUsuario);}
  requiereVinculo(user:Usuario){return this.rolesEditados[user.username]?.includes('FISCALIZADOR')&&!user.fiscalizadorId;}
  async cargarRoles(): Promise<void> {
    this.error = '';
    try {
      [this.roles, this.capacidades] = await Promise.all([this.service.listarRoles(), this.service.listarCapacidades()]);
      this.rolSeleccionado ||= this.roles[0]?.nombre ?? '';
    } catch { this.error = 'No se pudo cargar el catálogo de roles y capacidades.'; }
    this.cdr.markForCheck();
  }
  async crearUsuario(): Promise<void> {
    this.mensaje = ''; this.error = '';
    if (this.nuevo.password !== this.nuevo.confirmarPassword) { this.error = 'Las contraseñas no coinciden.'; return; }
    this.guardando = true;
    try {
      await this.service.crear(this.nuevo);
      this.mensaje = 'Usuario creado correctamente.';
      this.nuevo = { nombre: '', username: '', password: '', confirmarPassword: '', rol: 'MUNICIPIO', fiscalizadorId:'' };
      await Promise.all([this.cargarUsuarios(),this.cargarFiscalizadoresSinCuenta()]);
    } catch (error) { this.error = error instanceof HttpErrorResponse ? error.error?.message||'No se pudo crear el usuario.' : 'No se pudo crear el usuario.'; }
    finally { this.guardando = false; this.cdr.markForCheck(); }
  }
  async cambiarEstado(user: Usuario): Promise<void> {
    this.error = '';
    try { Object.assign(user, await this.service.cambiarEstado(user.username, !user.activo)); }
    catch { this.error = 'No se pudo cambiar el estado del usuario.'; }
    this.cdr.markForCheck();
  }
  async guardarRoles(user: Usuario, select: HTMLSelectElement, fiscalizador:HTMLSelectElement|undefined): Promise<void> {
    const selected = Array.from(select.selectedOptions).map(option => option.value as RolUsuario);
    if (!selected.length) { this.error = 'Cada usuario debe conservar al menos un rol.'; return; }
    this.error = '';
    try { Object.assign(user, await this.service.asignarRoles(user.username, selected,this.requiereVinculo(user)?fiscalizador?.value:undefined)); this.mensaje = `Roles actualizados para ${user.username}.`; await this.cargarFiscalizadoresSinCuenta(); }
    catch(e) { this.error = e instanceof HttpErrorResponse ? e.error?.message||'No se pudieron actualizar los roles.' : 'No se pudieron actualizar los roles.'; }
    this.cdr.markForCheck();
  }
  tieneCapacidad(code: string): boolean { return this.rolActual?.capacidades.includes(code) ?? false; }
  async alternarCapacidad(capability: Capacidad, input: HTMLInputElement): Promise<void> {
    const role = this.rolActual;
    if (!role?.configurable) { input.checked = this.tieneCapacidad(capability.codigo); return; }
    const codes = new Set(role.capacidades);
    input.checked ? codes.add(capability.codigo) : codes.delete(capability.codigo);
    this.error = ''; this.guardando = true;
    try {
      const updated = await this.service.guardarCapacidades(role.nombre, [...codes]);
      this.roles = this.roles.map(item => item.nombre === updated.nombre ? updated : item);
      this.mensaje = `Capacidades de ${updated.nombre} actualizadas.`;
    } catch { input.checked = this.tieneCapacidad(capability.codigo); this.error = 'No se pudieron guardar las capacidades del rol.'; }
    finally { this.guardando = false; this.cdr.markForCheck(); }
  }
}

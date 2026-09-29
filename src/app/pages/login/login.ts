import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  imports: [CommonModule, FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class LoginComponent implements OnInit {
  // Campos limpios para que el usuario escriba
  usuario = '';
  password = '';
  mostrarPassword = false;
  recordarSesion = true;

  mensajeError = '';
  mensajeExito = '';
  cargando = false;

  constructor(
    private router: Router,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    if (this.authService.isAuthenticated()) {
      void this.router.navigate([this.authService.session?.rol === 'FISCALIZADOR' ? '/perfil' : '/inicio']);
      return;
    }

    // Los campos inician COMPLETAMENTE VACÍOS para que el usuario escriba
    this.usuario = '';
    this.password = '';
  }

  iniciarSesion(): void {
    this.mensajeError = '';
    this.mensajeExito = '';

    const u = (this.usuario || '').trim().toLowerCase();
    const p = this.password || '';

    if (!u) {
      this.mensajeError = 'Por favor escribe tu usuario o correo electrónico.';
      return;
    }

    if (!p) {
      this.mensajeError = 'Por favor escribe tu contraseña.';
      return;
    }

    this.cargando = true;
    this.authService.login(u, p).then(session => {
      this.cargando = false;
      if (session.rol !== 'FISCALIZADOR' && session.rol !== 'MUNICIPIO') {
        this.authService.logout();
        this.mensajeError = 'No se pudo iniciar sesión.';
        return;
      }
      this.mensajeExito = 'Acceso concedido. Ingresando al sistema...';
      void this.router.navigate([session.rol === 'FISCALIZADOR' ? '/fiscalizador' : '/inicio']);
    }).catch((error: unknown) => {
      this.cargando = false;
      this.mensajeError = this.mensajeParaError(error);
    });
  }

  private mensajeParaError(error: unknown): string {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 401) return 'Usuario o contraseña incorrectos.';
    if (status === 0) return 'No se pudo conectar con el servidor.';
    if (status >= 500) return 'El servidor no pudo completar el inicio de sesión.';
    return 'No se pudo iniciar sesión.';
  }
}

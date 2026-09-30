import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {
  esRutaLogin = false;
  get esAdmin(): boolean { return this.authService.hasCapability('ROLES_VER'); }
  get esMunicipio(): boolean { return this.authService.hasCapability('SOLICITUD_VER'); }
  get esFiscalizador(): boolean { return this.authService.hasCapability('TAREAS_PROPIAS_VER'); }
  get puedeCrearSolicitud(): boolean { return this.authService.hasCapability('SOLICITUD_CREAR'); }
  get puedeVerUsuarios(): boolean { return this.authService.hasCapability('USUARIOS_VER'); }

  constructor(private router: Router, public authService: AuthService) {}

  ngOnInit(): void {
    this.actualizarEstado();
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => {
        this.actualizarEstado();
      });
  }

  actualizarEstado(): void {
    const url = this.router.url;
    this.esRutaLogin = url.includes('login') || url === '/' || url === '';
  }

  cerrarSesion(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}

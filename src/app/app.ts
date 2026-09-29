import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { Rol } from './models/solicitud.model';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {
  rolActivo: Rol = 'MUNICIPIO';
  esRutaLogin = false;
  get esMunicipio(): boolean { return this.authService.session?.rol === 'MUNICIPIO'; }
  get esFiscalizador(): boolean { return this.authService.session?.rol === 'FISCALIZADOR'; }

  constructor(private router: Router, private authService: AuthService) {}

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
    const rolSesion = this.authService.session?.rol;
    const guardado = localStorage.getItem('rolSM') as Rol;
    if (rolSesion === 'MUNICIPIO' || rolSesion === 'FISCALIZADOR') this.rolActivo = rolSesion;
    else if (guardado === 'MUNICIPIO' || guardado === 'FISCALIZADOR') this.rolActivo = guardado;
  }

  cerrarSesion(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}

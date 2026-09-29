import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Rol } from '../../models/solicitud.model';

@Component({
  selector: 'app-perfil',
  imports: [CommonModule],
  templateUrl: './perfil.html',
  styleUrl: './perfil.css',
})
export class PerfilComponent implements OnInit {
  rolActual: Rol = 'MUNICIPIO';

  constructor(private router: Router) {}

  ngOnInit(): void {
    const r = localStorage.getItem('rolSM') as Rol;
    if (r) {
      this.rolActual = r;
    }
    if (this.rolActual === 'FISCALIZADOR') {
      void this.router.navigate(['/fiscalizador']);
    }
  }

  entrar(rol: Rol): void {
    this.rolActual = rol;
    localStorage.setItem('rolSM', rol);
    if (rol === 'MUNICIPIO') {
      this.router.navigate(['/inicio']);
    }
  }
}
export { PerfilComponent as Perfil };

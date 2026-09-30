import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-perfil',
  imports: [CommonModule],
  templateUrl: './perfil.html',
  styleUrl: './perfil.css',
})
export class PerfilComponent implements OnInit {
  constructor(private router: Router, private auth: AuthService) {}

  get sesion() { return this.auth.session; }
  ngOnInit(): void {}
  entrar(): void { void this.router.navigate([this.auth.homeRoute()]); }
}
export { PerfilComponent as Perfil };

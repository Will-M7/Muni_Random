import { Routes } from '@angular/router';

import { LoginComponent } from './pages/login/login';
import { InicioComponent } from './pages/inicio/inicio';
import { NuevaCitaComponent } from './pages/nueva-cita/nueva-cita';
import { PredialComponent } from './pages/predial/predial';
import { SolicitudesComponent } from './pages/solicitudes/solicitudes';
import { MapaComponent } from './pages/mapa/mapa';
import { PerfilComponent } from './pages/perfil/perfil';
import { UsuariosComponent } from './pages/usuarios/usuarios';
import { FiscalizadorComponent } from './pages/fiscalizador/fiscalizador';
import { authGuard, roleGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full',
  },
  {
    path: 'login',
    component: LoginComponent,
  },
  {
    path: 'inicio',
    component: InicioComponent,
    canActivate: [authGuard, roleGuard], data: { roles: ['MUNICIPIO'] },
  },
  {
    path: 'nueva-solicitud',
    component: NuevaCitaComponent,
    canActivate: [authGuard, roleGuard], data: { roles: ['MUNICIPIO'] },
  },
  {
    path: 'nueva-solicitud/:codigo',
    component: NuevaCitaComponent,
    canActivate: [authGuard, roleGuard], data: { roles: ['MUNICIPIO'] },
  },
  { path: 'nueva-cita', redirectTo: 'nueva-solicitud', pathMatch: 'full' },
  { path: 'nueva-cita/:codigo', redirectTo: 'nueva-solicitud/:codigo', pathMatch: 'full' },
  {
    path: 'predial',
    component: PredialComponent,
    canActivate: [authGuard, roleGuard], data: { roles: ['MUNICIPIO'] },
  },
  {
    path: 'solicitudes',
    component: SolicitudesComponent,
    canActivate: [authGuard, roleGuard], data: { roles: ['MUNICIPIO'] },
  },
  {
    path: 'mapa',
    component: MapaComponent,
    canActivate: [authGuard],
  },
  {
    path: 'perfil',
    component: PerfilComponent,
    canActivate: [authGuard],
  },
  { path: 'fiscalizador', component: FiscalizadorComponent, canActivate: [authGuard, roleGuard], data: { roles: ['FISCALIZADOR'] } },
  { path: 'fiscalizador/ruta', component: FiscalizadorComponent, canActivate: [authGuard, roleGuard], data: { roles: ['FISCALIZADOR'] } },
  { path: 'fiscalizador/historial', component: FiscalizadorComponent, canActivate: [authGuard, roleGuard], data: { roles: ['FISCALIZADOR'] } },
  { path: 'usuarios', component: UsuariosComponent, canActivate: [authGuard, roleGuard], data: { roles: ['MUNICIPIO'] } },
  {
    path: '**',
    redirectTo: 'login',
  },
];

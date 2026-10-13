import { Routes } from '@angular/router';
import { LoginComponent } from './pages/login/login';
import { authGuard, capabilityGuard, adminGuard, homeGuard } from './guards/auth.guard';

const secured = (capability: string) => ({ canActivate: [authGuard, capabilityGuard], data: { capability } });

export const routes: Routes = [
  { path: '', canActivate:[homeGuard], children:[], pathMatch:'full' },
  { path: 'login', component: LoginComponent },
  { path: 'inicio', canActivate:[homeGuard], children:[], pathMatch:'full' },
  { path: 'nueva-solicitud', loadComponent:()=>import('./pages/nueva-cita/nueva-cita').then(m=>m.NuevaCitaComponent), ...secured('SOLICITUD_CREAR') },
  { path: 'nueva-solicitud/:codigo', loadComponent:()=>import('./pages/nueva-cita/nueva-cita').then(m=>m.NuevaCitaComponent), ...secured('SOLICITUD_EDITAR') },
  { path: 'nueva-cita', redirectTo: 'nueva-solicitud', pathMatch: 'full' },
  { path: 'nueva-cita/:codigo', redirectTo: 'nueva-solicitud/:codigo', pathMatch: 'full' },
  { path: 'predial', loadComponent:()=>import('./pages/predial/predial').then(m=>m.PredialComponent), ...secured('SOLICITUD_CREAR') },
  { path: 'solicitudes', loadComponent:()=>import('./pages/solicitudes/solicitudes').then(m=>m.SolicitudesComponent), ...secured('SOLICITUD_VER') },
  { path: 'expedientes', loadComponent:()=>import('./pages/expedientes/expedientes').then(m=>m.ExpedientesComponent), ...secured('EXPEDIENTE_VER') },
  { path: 'revisiones', loadComponent:()=>import('./pages/expedientes/revision-fiscalizacion').then(m=>m.RevisionBandejaComponent), ...secured('REVISION_VER') },
  { path: 'revisiones/:id', loadComponent:()=>import('./pages/expedientes/revision-fiscalizacion').then(m=>m.RevisionDetalleComponent), ...secured('REVISION_VER') },
  { path: 'expedientes/nuevo', loadComponent:()=>import('./pages/expedientes/expediente-nuevo').then(m=>m.ExpedienteNuevoComponent), ...secured('EXPEDIENTE_CREAR') },
  { path: 'expedientes/:codigo', loadComponent:()=>import('./pages/expedientes/expediente-detalle').then(m=>m.ExpedienteDetalleComponent), ...secured('EXPEDIENTE_VER') },
  { path: 'mapa', loadComponent:()=>import('./pages/mapa/mapa').then(m=>m.MapaComponent), ...secured('SOLICITUD_VER') },
  { path: 'perfil', loadComponent:()=>import('./pages/perfil/perfil').then(m=>m.PerfilComponent), canActivate: [authGuard] },
  { path: 'fiscalizador', loadComponent:()=>import('./pages/fiscalizador/fiscalizador').then(m=>m.FiscalizadorComponent), ...secured('TAREAS_PROPIAS_VER') },
  { path: 'fiscalizador/proximas', loadComponent:()=>import('./pages/fiscalizador/fiscalizador').then(m=>m.FiscalizadorComponent), ...secured('TAREAS_PROPIAS_VER') },
  { path: 'fiscalizador/visita/:codigo', loadComponent:()=>import('./pages/fiscalizador/fiscalizador').then(m=>m.FiscalizadorComponent), ...secured('TAREAS_PROPIAS_VER') },
  { path: 'fiscalizador/ruta', loadComponent:()=>import('./pages/fiscalizador/fiscalizador').then(m=>m.FiscalizadorComponent), ...secured('RUTA_VER') },
  { path: 'fiscalizador/historial', loadComponent:()=>import('./pages/fiscalizador/fiscalizador').then(m=>m.FiscalizadorComponent), ...secured('TAREAS_PROPIAS_VER') },
  { path: 'usuarios', redirectTo: 'admin', pathMatch: 'full' },
  { path: 'admin', loadComponent:()=>import('./pages/admin/admin').then(m=>m.AdminComponent), canActivate:[authGuard,adminGuard] },
  { path: 'fiscalizador/diligencia/:programacionId', loadComponent:()=>import('./pages/diligencia/diligencia').then(m=>m.DiligenciaComponent), ...secured('DILIGENCIA_INICIAR') },
  { path: '**', canActivate:[homeGuard], children:[] },
];

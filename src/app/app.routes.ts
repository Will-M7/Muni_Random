import { Routes } from '@angular/router';
import { LoginComponent } from './pages/login/login';
import { InicioComponent } from './pages/inicio/inicio';
import { NuevaCitaComponent } from './pages/nueva-cita/nueva-cita';
import { PredialComponent } from './pages/predial/predial';
import { SolicitudesComponent } from './pages/solicitudes/solicitudes';
import { MapaComponent } from './pages/mapa/mapa';
import { PerfilComponent } from './pages/perfil/perfil';
import { FiscalizadorComponent } from './pages/fiscalizador/fiscalizador';
import { ExpedientesComponent } from './pages/expedientes/expedientes';
import { ExpedienteNuevoComponent } from './pages/expedientes/expediente-nuevo';
import { ExpedienteDetalleComponent } from './pages/expedientes/expediente-detalle';
import { AdminComponent } from './pages/admin/admin';
import { DiligenciaComponent } from './pages/diligencia/diligencia';
import { RevisionBandejaComponent, RevisionDetalleComponent } from './pages/expedientes/revision-fiscalizacion';
import { authGuard, capabilityGuard, adminGuard } from './guards/auth.guard';

const secured = (capability: string) => ({ canActivate: [authGuard, capabilityGuard], data: { capability } });

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'inicio', component: InicioComponent, ...secured('SOLICITUD_VER') },
  { path: 'nueva-solicitud', component: NuevaCitaComponent, ...secured('SOLICITUD_CREAR') },
  { path: 'nueva-solicitud/:codigo', component: NuevaCitaComponent, ...secured('SOLICITUD_EDITAR') },
  { path: 'nueva-cita', redirectTo: 'nueva-solicitud', pathMatch: 'full' },
  { path: 'nueva-cita/:codigo', redirectTo: 'nueva-solicitud/:codigo', pathMatch: 'full' },
  { path: 'predial', component: PredialComponent, ...secured('SOLICITUD_CREAR') },
  { path: 'solicitudes', component: SolicitudesComponent, ...secured('SOLICITUD_VER') },
  { path: 'expedientes', component: ExpedientesComponent, ...secured('EXPEDIENTE_VER') },
  { path: 'revisiones', component: RevisionBandejaComponent, ...secured('REVISION_VER') },
  { path: 'revisiones/:id', component: RevisionDetalleComponent, ...secured('REVISION_VER') },
  { path: 'expedientes/nuevo', component: ExpedienteNuevoComponent, ...secured('EXPEDIENTE_CREAR') },
  { path: 'expedientes/:codigo', component: ExpedienteDetalleComponent, ...secured('EXPEDIENTE_VER') },
  { path: 'mapa', component: MapaComponent, ...secured('SOLICITUD_VER') },
  { path: 'perfil', component: PerfilComponent, canActivate: [authGuard] },
  { path: 'fiscalizador', component: FiscalizadorComponent, ...secured('TAREAS_PROPIAS_VER') },
  { path: 'fiscalizador/ruta', component: FiscalizadorComponent, ...secured('RUTA_VER') },
  { path: 'fiscalizador/historial', component: FiscalizadorComponent, ...secured('TAREAS_PROPIAS_VER') },
  { path: 'usuarios', redirectTo: 'admin', pathMatch: 'full' },
  { path: 'admin', component: AdminComponent, canActivate:[authGuard,adminGuard] },
  { path: 'fiscalizador/diligencia/:programacionId', component: DiligenciaComponent, ...secured('DILIGENCIA_INICIAR') },
  { path: '**', redirectTo: 'login' },
];

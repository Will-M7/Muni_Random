import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { EstadoSolicitud, Solicitud } from '../../models/solicitud.model';
import { VerificacionService } from '../../services/verificacion.service';

@Component({
  selector: 'app-solicitudes',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './solicitudes.html',
  styleUrl: './solicitudes.css',
})
export class SolicitudesComponent implements OnInit {
  solicitudes: Solicitud[] = [];
  solicitudesFiltradas: Solicitud[] = [];

  terminoBusqueda = '';
  filtroEstado: string = 'TODOS';

  estadosDisponibles: string[] = [
    'TODOS',
    'En espera',
    'Verificado',
    'Observada',
    'Expirado',
  ];

  // Modal Ver Detalle
  solicitudDetalle: Solicitud | null = null;
  modalDetalleVisible = false;

  // Modal Documento
  modalDocVisible = false;
  docSeleccionado = '';
  cargando = false;
  errorCarga = '';

  constructor(
    private verificacionService: VerificacionService,
    private router: Router,
    private changeDetector: ChangeDetectorRef
  ) {}

  async ngOnInit(): Promise<void> {
    await this.cargarSolicitudes();
  }

  async cargarSolicitudes(): Promise<void> {
    this.cargando = true;
    this.errorCarga = '';
    try {
      this.solicitudes = [...await this.verificacionService.obtenerSolicitudes()];
      this.aplicarFiltros();
    } catch {
      this.errorCarga = 'No se pudieron cargar las solicitudes. Intenta nuevamente.';
      this.solicitudes = [];
      this.solicitudesFiltradas = [];
    } finally {
      this.cargando = false;
      this.changeDetector.markForCheck();
    }
  }

  aplicarFiltros(): void {
    let res = [...this.solicitudes];

    if (this.filtroEstado !== 'TODOS') {
      res = res.filter(s => s.estado === this.filtroEstado);
    }

    if (this.terminoBusqueda.trim()) {
      const q = this.terminoBusqueda.toLowerCase().trim();
      res = res.filter(
        s =>
          s.codigo.toLowerCase().includes(q) ||
          s.nombre.toLowerCase().includes(q) ||
          s.dni.includes(q) ||
          s.direccion.toLowerCase().includes(q) ||
          (s.fiscalizadorNombre && s.fiscalizadorNombre.toLowerCase().includes(q))
      );
    }

    this.solicitudesFiltradas = res;
  }

  verDetalle(solicitud: Solicitud): void {
    this.solicitudDetalle = solicitud;
    this.modalDetalleVisible = true;
  }

  editarSolicitud(codigo: string): void {
    this.modalDetalleVisible = false;
    this.router.navigate(['/nueva-solicitud', codigo]);
  }

  async abrirDocumento(codigo: string, event?: Event): Promise<void> {
    if (event) event.stopPropagation();
    try { const blob = await this.verificacionService.abrirDocumento(codigo); const url = URL.createObjectURL(blob); window.open(url, '_blank', 'noopener'); setTimeout(() => URL.revokeObjectURL(url), 60_000); }
    catch { this.docSeleccionado = 'No se pudo abrir el documento.'; this.modalDocVisible = true; }
  }
  async abrirReporte(codigo: string, event?: Event): Promise<void> { if (event) event.stopPropagation(); try { const blob=await this.verificacionService.abrirReporte(codigo); const url=URL.createObjectURL(blob); window.open(url,'_blank','noopener'); setTimeout(()=>URL.revokeObjectURL(url),60000); } catch { this.docSeleccionado='No se pudo abrir el reporte de fiscalización.'; this.modalDocVisible=true; } }

  cerrarModales(): void {
    this.modalDetalleVisible = false;
    this.modalDocVisible = false;
  }

  async cambiarEstadoDirecto(codigo: string, nuevoEstado: EstadoSolicitud): Promise<void> {
    await this.verificacionService.cambiarEstado(codigo, nuevoEstado);
    await this.cargarSolicitudes();
    if (this.solicitudDetalle && this.solicitudDetalle.codigo === codigo) {
      this.solicitudDetalle = this.verificacionService.obtenerSolicitudPorCodigo(codigo) || null;
    }
  }

  getBadgeClass(estado: string): string {
    return 'status-' + estado.toLowerCase().replace(/\s+/g, '-');
  }
}
export { SolicitudesComponent as Solicitudes };

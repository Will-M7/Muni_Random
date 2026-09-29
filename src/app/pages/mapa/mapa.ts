import { ChangeDetectorRef, Component, AfterViewInit, OnDestroy, OnInit, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import * as L from 'leaflet';
import { MUNICIPALIDAD_SAN_MIGUEL_COORDS, VerificacionService } from '../../services/verificacion.service';

@Component({
  selector: 'app-mapa',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './mapa.html',
  styleUrl: './mapa.css',
})
export class MapaComponent implements OnInit, AfterViewInit, OnDestroy {
  private mapa!: L.Map;
  private marcador!: L.Marker;

  latitudActual = MUNICIPALIDAD_SAN_MIGUEL_COORDS[0];
  longitudActual = MUNICIPALIDAD_SAN_MIGUEL_COORDS[1];
  coordenadasSeleccionadas = false;

  codigoSolicitud = '';
  solicitanteNombre = '';
  direccionPredio = '';
  origenRuta = 'nueva-solicitud';
  terminoBusqueda = '';
  resultadosBusqueda: Array<{ display_name: string; lat: string; lon: string }> = [];
  buscando = false;
  mapaMensaje = '';

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private verificacionService: VerificacionService,
    private zone: NgZone,
    private changeDetector: ChangeDetectorRef
  ) {}

  async ngOnInit(): Promise<void> {
    const qp = this.route.snapshot.queryParamMap;
    this.codigoSolicitud = qp.get('codigo') || qp.get('edit') || '';
    this.origenRuta = qp.get('retorno') || 'nueva-solicitud';

    if (this.codigoSolicitud) {
      const s = await this.verificacionService.cargarSolicitud(this.codigoSolicitud);
      if (s) {
        this.solicitanteNombre = s.nombre;
        this.direccionPredio = s.direccion;
        this.latitudActual = s.latitud || MUNICIPALIDAD_SAN_MIGUEL_COORDS[0];
        this.longitudActual = s.longitud || MUNICIPALIDAD_SAN_MIGUEL_COORDS[1];
        this.coordenadasSeleccionadas = true;
        this.changeDetector.markForCheck();
      }
    } else {
      const guardada = localStorage.getItem('ubicacionVivienda');
      if (guardada) {
        try {
          const punto = JSON.parse(guardada);
          if (punto.latitud && punto.longitud) {
            this.latitudActual = punto.latitud;
            this.longitudActual = punto.longitud;
            this.coordenadasSeleccionadas = true;
          }
        } catch {
          // Fallback a San Miguel
        }
      }
    }
  }

  ngAfterViewInit(): void {
    // Configurar iconos de Leaflet para evitar problemas de assets perdidos
    const iconoDefault = L.icon({
      iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
      iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
      shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
      iconSize: [25, 41],
      iconAnchor: [12, 41],
      popupAnchor: [1, -34],
      shadowSize: [41, 41],
    });
    L.Marker.prototype.options.icon = iconoDefault;

    // Inicializar mapa centrado en San Miguel
    this.mapa = L.map('mapa').setView([this.latitudActual, this.longitudActual], 15);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap · Municipalidad de San Miguel',
      maxZoom: 19,
    }).addTo(this.mapa);

    if (this.coordenadasSeleccionadas) {
      this.colocarMarcador(this.latitudActual, this.longitudActual);
    }

    this.mapa.on('click', (evento: L.LeafletMouseEvent) => {
      this.zone.run(() => {
        const lat = evento.latlng.lat;
        const lng = evento.latlng.lng;
        this.latitudActual = lat;
        this.longitudActual = lng;
        this.coordenadasSeleccionadas = true;
        this.colocarMarcador(lat, lng);
      });
    });
  }

  private colocarMarcador(lat: number, lng: number): void {
    if (this.marcador) {
      this.mapa.removeLayer(this.marcador);
    }

    const popupHtml = `
      <div style="font-family: inherit; font-size: 12px; line-height: 1.4;">
        <strong style="color: #147c50;">Domicilio seleccionado</strong><br>
        ${this.solicitanteNombre ? `<b>Titular:</b> ${this.solicitanteNombre}<br>` : ''}
        ${this.direccionPredio ? `<b>Dirección:</b> ${this.direccionPredio}<br>` : ''}
        <span>Lat: ${lat.toFixed(6)}<br>Lng: ${lng.toFixed(6)}</span>
      </div>
    `;

    this.marcador = L.marker([lat, lng])
      .addTo(this.mapa)
      .bindPopup(popupHtml)
      .openPopup();
  }

  async buscarUbicacion(): Promise<void> {
    const q = this.terminoBusqueda.trim();
    if (!q) return;
    this.buscando = true;
    try {
      const response = await fetch(`https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&countrycodes=pe&q=${encodeURIComponent(q)}`, { headers: { 'Accept-Language': 'es' } });
      if (!response.ok) throw new Error('geocoding');
      const resultados = await response.json() as Array<{ display_name: string; lat: string; lon: string }>;
      this.zone.run(() => { this.resultadosBusqueda = resultados; this.buscando = false; });
    } catch { this.zone.run(() => { this.resultadosBusqueda = []; this.buscando = false; }); }
  }

  seleccionarResultado(resultado: { lat: string; lon: string }): void {
    this.latitudActual = Number(resultado.lat); this.longitudActual = Number(resultado.lon); this.coordenadasSeleccionadas = true;
    this.mapa.setView([this.latitudActual, this.longitudActual], 17); this.colocarMarcador(this.latitudActual, this.longitudActual); this.resultadosBusqueda = [];
  }

  async guardarUbicacion(): Promise<void> {
    if (!this.coordenadasSeleccionadas || !this.marcador) {
      this.mapaMensaje = 'Selecciona un punto en el mapa antes de confirmar la ubicación.';
      return;
    }
    this.mapaMensaje = '';

    const payload = {
      latitud: this.latitudActual,
      longitud: this.longitudActual,
    };

    localStorage.setItem('sm_temp_ubicacion_mapa', JSON.stringify(payload));
    localStorage.setItem('ubicacionVivienda', JSON.stringify(payload));

    this.router.navigate(this.codigoSolicitud ? ['/nueva-solicitud', this.codigoSolicitud] : ['/nueva-solicitud']);
  }

  volver(): void {
    if (this.codigoSolicitud) {
      this.router.navigate(['/nueva-solicitud', this.codigoSolicitud]);
    } else {
      this.router.navigate(['/nueva-solicitud']);
    }
  }

  ngOnDestroy(): void {
    if (this.mapa) {
      this.mapa.remove();
    }
  }
}
export { MapaComponent as Mapa };

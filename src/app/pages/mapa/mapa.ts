import { ChangeDetectorRef, Component, AfterViewInit, OnDestroy, OnInit, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import * as L from 'leaflet';
import { MUNICIPALIDAD_SAN_MIGUEL_COORDS, VerificacionService } from '../../services/verificacion.service';
import { Solicitud } from '../../models/solicitud.model';

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
  private iconoSolicitud!: L.DivIcon;
  private marcadoresSolicitud = new Map<string, L.Marker>();

  latitudActual = MUNICIPALIDAD_SAN_MIGUEL_COORDS[0];
  longitudActual = MUNICIPALIDAD_SAN_MIGUEL_COORDS[1];
  coordenadasSeleccionadas = false;

  codigoSolicitud = '';
  solicitanteNombre = '';
  telefonoSolicitante = '';
  direccionPredio = '';
  origenRuta = 'nueva-solicitud';
  terminoBusqueda = '';
  resultadosBusqueda: Array<{ display_name: string; lat: string; lon: string }> = [];
  solicitudes: Solicitud[] = [];
  solicitudesFiltradas: Solicitud[] = [];
  solicitudExpandida: string | null = null;
  solicitudesError = '';
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
    this.terminoBusqueda = qp.get('q') || '';

    if (this.origenRuta === 'expedientes/nuevo') {
      try {
        const borrador = JSON.parse(sessionStorage.getItem('sm_expediente_nuevo_borrador') || '{}');
        this.solicitanteNombre = [borrador.nombres, borrador.apellidos].filter(Boolean).join(' ');
        this.telefonoSolicitante = borrador.telefono || '';
        this.direccionPredio = borrador.direccion || this.terminoBusqueda;
        const lat = Number(borrador.latitud), lng = Number(borrador.longitud);
        if (borrador.latitud !== null && borrador.longitud !== null && Number.isFinite(lat) && Number.isFinite(lng)) {
          this.latitudActual = lat;
          this.longitudActual = lng;
          this.coordenadasSeleccionadas = true;
        }
      } catch { /* El mapa puede abrirse aunque el borrador esté vacío. */ }
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

    try {
      this.solicitudes = await this.verificacionService.obtenerSolicitudes();
      this.solicitudesFiltradas = this.solicitudes;
      const solicitudActual = this.solicitudes.find(s => s.codigo === this.codigoSolicitud);
      if (solicitudActual) {
        this.solicitanteNombre = solicitudActual.nombre;
        this.telefonoSolicitante = solicitudActual.telefono || '';
        this.direccionPredio = solicitudActual.direccion;
        if (this.tieneCoordenadas(solicitudActual)) {
          this.latitudActual = Number(solicitudActual.latitud);
          this.longitudActual = Number(solicitudActual.longitud);
          this.coordenadasSeleccionadas = true;
        }
        this.solicitudExpandida = solicitudActual.codigo;
      }
      if (this.mapa) {
        this.pintarMarcadoresSolicitudes();
        if (solicitudActual && this.tieneCoordenadas(solicitudActual)) {
          this.mapa.setView([Number(solicitudActual.latitud), Number(solicitudActual.longitud)], 16);
          this.colocarMarcador(Number(solicitudActual.latitud), Number(solicitudActual.longitud));
        }
      }
    } catch {
      this.solicitudesError = 'No se pudieron cargar las solicitudes registradas.';
    }
    this.changeDetector.markForCheck();
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

    this.iconoSolicitud = L.divIcon({
      className: 'request-map-pin',
      html: '<svg xmlns="http://www.w3.org/2000/svg" width="36" height="44" viewBox="0 0 36 44" aria-hidden="true"><path fill="#dc2626" stroke="#fff" stroke-width="2" d="M18 2C9.2 2 2 9.2 2 18c0 10.4 16 24 16 24s16-13.6 16-24C34 9.2 26.8 2 18 2Z"/><circle cx="18" cy="18" r="5" fill="#fff"/></svg>',
      iconSize: [36, 44],
      iconAnchor: [18, 43],
      popupAnchor: [0, -39],
    });

    // Inicializar mapa centrado en San Miguel
    this.mapa = L.map('mapa').setView([this.latitudActual, this.longitudActual], 15);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap · Municipalidad de San Miguel',
      maxZoom: 19,
    }).addTo(this.mapa);

    if (this.coordenadasSeleccionadas) {
      this.colocarMarcador(this.latitudActual, this.longitudActual);
    }
    this.pintarMarcadoresSolicitudes();

    this.mapa.on('click', (evento: L.LeafletMouseEvent) => {
      this.zone.run(() => {
        const lat = evento.latlng.lat;
        const lng = evento.latlng.lng;
        this.latitudActual = lat;
        this.longitudActual = lng;
        this.coordenadasSeleccionadas = true;
        this.colocarMarcador(lat, lng);
        this.changeDetector.markForCheck();
      });
    });
  }

  private colocarMarcador(lat: number, lng: number): void {
    if (this.marcador) {
      this.mapa.removeLayer(this.marcador);
    }

    const popup = document.createElement('div');
    popup.style.cssText = 'font-family:Arial,sans-serif;font-size:12px;line-height:1.45;min-width:180px';
    const title = document.createElement('strong');
    title.style.color = '#a71919';
    title.textContent = this.solicitanteNombre || 'Domicilio seleccionado';
    popup.append(title);
    if (this.direccionPredio) {
      const address = document.createElement('div');
      address.textContent = this.direccionPredio;
      popup.append(address);
    }
    if (this.telefonoSolicitante) {
      const phone = document.createElement('div');
      phone.textContent = `Teléfono: ${this.telefonoSolicitante}`;
      popup.append(phone);
    }
    const coords = document.createElement('small');
    coords.textContent = `Lat: ${lat.toFixed(6)} · Lng: ${lng.toFixed(6)}`;
    popup.append(coords);
    this.marcador = L.marker([lat, lng], this.iconoSolicitud ? { icon: this.iconoSolicitud } : {})
      .addTo(this.mapa)
      .bindPopup(popup)
      .openPopup();
  }

  private pintarMarcadoresSolicitudes(): void {
    if (!this.mapa || !this.iconoSolicitud) return;
    this.marcadoresSolicitud.forEach(marker => this.mapa.removeLayer(marker));
    this.marcadoresSolicitud.clear();
    this.solicitudes.filter(s => this.tieneCoordenadas(s)).forEach(s => {
      const marker = L.marker([Number(s.latitud), Number(s.longitud)], { icon: this.iconoSolicitud })
        .addTo(this.mapa)
        .bindPopup(this.popupSolicitud(s));
      this.marcadoresSolicitud.set(s.codigo, marker);
    });
  }

  private popupSolicitud(s: Solicitud): string {
    const esc = (value: string | null | undefined) => (value || '').replace(/[&<>"']/g, character => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    }[character] || character));
    return `<div style="font-family:Arial,sans-serif;font-size:13px;line-height:1.45;min-width:190px"><strong style="color:#a71919">${esc(s.nombre)}</strong><br><span>${esc(s.direccion || 'Dirección no registrada')}</span>${s.referencia ? `<br><small>Referencia: ${esc(s.referencia)}</small>` : ''}${s.telefono ? `<br><b>Teléfono:</b> ${esc(s.telefono)}` : ''}</div>`;
  }

  tieneCoordenadas(s: Solicitud): boolean {
    const lat = Number(s.latitud);
    const lng = Number(s.longitud);
    return Number.isFinite(lat) && Number.isFinite(lng) && lat !== 0 && lng !== 0;
  }

  enfocarSolicitud(s: Solicitud): void {
    const marker = this.marcadoresSolicitud.get(s.codigo);
    if (!marker) {
      this.mapaMensaje = 'Esta solicitud todavía no tiene una ubicación marcada.';
      return;
    }
    this.mapaMensaje = '';
    this.mapa.setView([Number(s.latitud), Number(s.longitud)], 17, { animate: true });
    marker.openPopup();
  }

  toggleSolicitud(codigo: string): void {
    this.solicitudExpandida = this.solicitudExpandida === codigo ? null : codigo;
  }

  private normalizar(texto: string | null | undefined): string {
    return (texto || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase();
  }

  private pareceDireccion(texto: string): boolean {
    const normalizada = this.normalizar(texto).trim();
    if (/^[\d\s+().-]+$/.test(normalizada)) return false;
    return /\b(jr|jiron|calle|avenida|av|pasaje|psje|prolongacion|carretera|urbanizacion|urb|manzana|mz|lote|lt)\b/.test(normalizada)
      || /\b(nro|numero|num|n|casa|#)\s*[°º.\-]*\s*\d/.test(normalizada)
      || /^\d{1,4}\s+/.test(normalizada);
  }

  async buscarUbicacion(): Promise<void> {
    const q = this.terminoBusqueda.trim();
    if (!q) {
      this.solicitudesFiltradas = this.solicitudes;
      this.resultadosBusqueda = [];
      this.mapaMensaje = '';
      this.changeDetector.markForCheck();
      return;
    }
    this.buscando = true;
    this.mapaMensaje = '';
    this.resultadosBusqueda = [];
    const busqueda = this.normalizar(q);
    this.solicitudesFiltradas = this.solicitudes.filter(s =>
      this.normalizar([s.codigo, s.nombre, s.dni, s.telefono, s.direccion, s.referencia].join(' ')).includes(busqueda)
    );
    if (this.solicitudesFiltradas.length) {
      const ubicadas = this.solicitudesFiltradas.filter(s => this.tieneCoordenadas(s));
      if (ubicadas.length === 1) {
        this.enfocarSolicitud(ubicadas[0]);
      } else if (ubicadas.length > 1) {
        const puntos = ubicadas.map(s => L.latLng(Number(s.latitud), Number(s.longitud)));
        this.mapa.fitBounds(L.latLngBounds(puntos), { padding: [36, 36], maxZoom: 16 });
      } else {
        this.mapaMensaje = 'Encontré la solicitud, pero todavía no tiene una ubicación marcada.';
      }
      this.buscando = false;
      this.changeDetector.markForCheck();
      return;
    }

    if (!this.pareceDireccion(q)) {
      this.buscando = false;
      this.mapaMensaje = 'Para buscar un punto en el mapa, escribe un jirón, calle o dirección. La búsqueda por persona se revisa solo en las solicitudes registradas.';
      this.changeDetector.markForCheck();
      return;
    }

    this.solicitudesError = '';
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 10000);
    try {
      const response = await fetch(`https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&countrycodes=pe&q=${encodeURIComponent(q)}`, { headers: { 'Accept-Language': 'es' }, signal: controller.signal });
      if (!response.ok) throw new Error('geocoding');
      const resultados = await response.json() as Array<{ display_name: string; lat: string; lon: string }>;
      this.zone.run(() => {
        this.resultadosBusqueda = resultados;
        if (!resultados.length) this.mapaMensaje = 'No hay solicitudes que coincidan ni se encontró esa dirección. Prueba con el nombre del jirón o la persona.';
        this.buscando = false;
        this.changeDetector.markForCheck();
      });
    } catch {
      this.zone.run(() => {
        this.resultadosBusqueda = [];
        this.mapaMensaje = 'No se pudo consultar la dirección en el mapa. La búsqueda de solicitudes registradas sí está disponible.';
        this.buscando = false;
        this.changeDetector.markForCheck();
      });
    } finally {
      window.clearTimeout(timeout);
    }
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
    if (this.origenRuta !== 'expedientes/nuevo') localStorage.setItem('ubicacionVivienda', JSON.stringify(payload));

    if (this.origenRuta === 'expedientes/nuevo') this.router.navigate(['/expedientes/nuevo']);
    else this.router.navigate(this.codigoSolicitud ? ['/nueva-solicitud', this.codigoSolicitud] : ['/nueva-solicitud']);
  }

  volver(): void {
    if (this.origenRuta === 'expedientes/nuevo') { this.router.navigate(['/expedientes/nuevo']); return; }
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

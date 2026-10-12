import { ChangeDetectorRef, Component, AfterViewInit, OnDestroy, OnInit, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import * as L from 'leaflet';
import { MUNICIPALIDAD_SAN_MIGUEL_COORDS, VerificacionService } from '../../services/verificacion.service';
import { Solicitud } from '../../models/solicitud.model';

export interface GeoSuggestion { display_name: string; lat: string; lon: string; }

export function parseCoordinates(raw: string): [number, number] | null {
  const decimal = raw.trim().match(/^([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s*(?:,\s*|\s+)\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+))$/);
  if (decimal) return validPoint(Number(decimal[1]), Number(decimal[2]));

  const dms = [...raw.matchAll(/(?:^|\s)([NSEWO])?\s*(\d{1,3})(?:[.,](\d+))?\s*[°º]\s*(\d{1,2})(?:[.,](\d+))?\s*(?:['′]\s*(\d{1,2}(?:[.,]\d+)?)\s*(?:["″])?)?\s*([NSEWO])?(?=$|\s)/gi)];
  if (dms.length !== 2) return null;
  const values = dms.map(match => {
    const degrees = Number(match[2] + (match[3] ? `.${match[3]}` : ''));
    const minutes = Number(match[4] + (match[5] ? `.${match[5]}` : ''));
    const seconds = match[6] ? Number(match[6].replace(',', '.')) : 0;
    const direction = (match[1] || match[7] || '').toUpperCase();
    const negative = direction === 'S' || direction === 'W' || direction === 'O';
    if (!Number.isFinite(degrees + minutes + seconds) || minutes >= 60 || seconds >= 60 || (direction && !'NSEWO'.includes(direction))) return null;
    return (negative ? -1 : 1) * (Math.abs(degrees) + minutes / 60 + seconds / 3600);
  });
  if (values.some(value => value === null)) return null;
  let [lat, lon] = values as number[];
  const directions = dms.map(match => (match[1] || match[7] || '').toUpperCase());
  if (directions.some(d => d === 'E' || d === 'W' || d === 'O') && (directions[0] === 'E' || directions[0] === 'W' || directions[0] === 'O')) [lat, lon] = [lon, lat];
  return validPoint(lat, lon);
}

function validPoint(lat: number, lon: number): [number, number] | null {
  return Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180 ? [lat, lon] : null;
}

export function normalizedGeocodeQuery(raw: string): string {
  let q = raw.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es').replace(/\s+/g, ' ').trim();
  q = q.replace(/\b(jr|jron|jiron)\b\.?/g, 'jiron').replace(/\b(av|avda)\b\.?/g, 'avenida').replace(/\b(psje|psj)\b\.?/g, 'pasaje');
  return /\b(san miguel|juliaca|puno|peru)\b/.test(q) ? q : `${q}, San Miguel, Juliaca, Puno, Perú`;
}

@Component({ selector: 'app-mapa', standalone: true, imports: [CommonModule, FormsModule], templateUrl: './mapa.html', styleUrl: './mapa.css' })
export class MapaComponent implements OnInit, AfterViewInit, OnDestroy {
  private mapa!: L.Map;
  private marcador?: L.Marker;
  private iconoSolicitud!: L.DivIcon;
  private searchAbort?: AbortController;
  private searchSequence = 0;
  private lastGeocodeAt = 0;
  latitudActual = MUNICIPALIDAD_SAN_MIGUEL_COORDS[0];
  longitudActual = MUNICIPALIDAD_SAN_MIGUEL_COORDS[1];
  coordenadasSeleccionadas = false;
  codigoSolicitud = '';
  solicitanteNombre = '';
  telefonoSolicitante = '';
  direccionPredio = '';
  origenRuta = 'nueva-solicitud';
  terminoBusqueda = '';
  resultadosBusqueda: GeoSuggestion[] = [];
  buscando = false;
  mapaMensaje = '';
  busquedaAmpliada = false;
  etiquetaPunto = '';

  constructor(private router: Router, private route: ActivatedRoute, private verificacionService: VerificacionService,
    private zone: NgZone, private changeDetector: ChangeDetectorRef) {}

  async ngOnInit(): Promise<void> {
    const qp = this.route.snapshot.queryParamMap;
    this.codigoSolicitud = qp.get('codigo') || qp.get('edit') || '';
    this.origenRuta = qp.get('retorno') || 'nueva-solicitud';
    this.terminoBusqueda = qp.get('q') || '';
    if (this.origenRuta === 'expedientes/nuevo') {
      try {
        const draft = JSON.parse(sessionStorage.getItem('sm_expediente_nuevo_borrador') || '{}');
        this.solicitanteNombre = [draft.nombres, draft.apellidos].filter(Boolean).join(' ');
        this.telefonoSolicitante = draft.telefono || '';
        this.direccionPredio = draft.direccion || this.terminoBusqueda;
        const point = validPoint(Number(draft.latitud), Number(draft.longitud));
        if (draft.latitud !== null && draft.longitud !== null && point) {
          [this.latitudActual, this.longitudActual] = point;
          this.coordenadasSeleccionadas = true;
        }
      } catch { /* El mapa puede abrirse aunque el borrador esté vacío. */ }
    } else {
      try {
        const saved = JSON.parse(localStorage.getItem('ubicacionVivienda') || 'null');
        const point = saved && validPoint(Number(saved.latitud), Number(saved.longitud));
        if (point) { [this.latitudActual, this.longitudActual] = point; this.coordenadasSeleccionadas = true; }
      } catch { /* Se conserva el centro institucional. */ }
      if (this.codigoSolicitud) {
        try {
          const solicitud = await this.verificacionService.cargarSolicitud(this.codigoSolicitud);
          this.solicitanteNombre = solicitud.nombre;
          this.telefonoSolicitante = solicitud.telefono || '';
          this.direccionPredio = solicitud.direccion || this.terminoBusqueda;
          const point = validPoint(Number(solicitud.latitud), Number(solicitud.longitud));
          if (point) { [this.latitudActual, this.longitudActual] = point; this.coordenadasSeleccionadas = true; }
        } catch { this.mapaMensaje = 'No se pudo consultar la ubicación guardada para esta solicitud.'; }
      }
    }
    this.changeDetector.markForCheck();
  }

  ngAfterViewInit(): void {
    L.Marker.prototype.options.icon = L.icon({
      iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
      iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
      shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
      iconSize: [25, 41], iconAnchor: [12, 41], popupAnchor: [1, -34], shadowSize: [41, 41],
    });
    this.iconoSolicitud = L.divIcon({ className: 'selected-map-pin', html: '<span aria-hidden="true"></span>', iconSize: [30, 38], iconAnchor: [15, 36] });
    this.mapa = L.map('mapa', { zoomControl: true }).setView([this.latitudActual, this.longitudActual], this.coordenadasSeleccionadas ? 17 : 15);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>', maxZoom: 19,
    }).addTo(this.mapa);
    if (this.coordenadasSeleccionadas) this.colocarMarcador(this.latitudActual, this.longitudActual);
    this.mapa.on('click', event => this.zone.run(() => this.seleccionarPunto(event.latlng.lat, event.latlng.lng)));
    window.setTimeout(() => this.mapa?.invalidateSize({ pan: false }), 0);
  }

  private colocarMarcador(lat: number, lon: number): void {
    this.marcador?.remove();
    this.marcador = L.marker([lat, lon], { icon: this.iconoSolicitud, draggable: true, title: 'Ubicación del predio' }).addTo(this.mapa);
    this.marcador.on('dragend', () => this.zone.run(() => {
      const p = this.marcador!.getLatLng(); this.seleccionarPunto(p.lat, p.lng, false);
    }));
    this.actualizarPopup();
  }

  private actualizarPopup(): void {
    if (!this.marcador) return;
    const popup = document.createElement('div');
    const title = document.createElement('strong'); title.textContent = this.etiquetaPunto || this.direccionPredio || this.solicitanteNombre || 'Predio seleccionado'; popup.append(title);
    const coords = document.createElement('div'); coords.textContent = `${this.latitudActual.toFixed(6)}, ${this.longitudActual.toFixed(6)}`; popup.append(coords);
    this.marcador.bindPopup(popup).openPopup();
  }

  private seleccionarPunto(lat: number, lon: number, recenter = true, label = ''): void {
    const point = validPoint(lat, lon);
    if (!point) { this.mapaMensaje = 'Las coordenadas deben estar dentro de los rangos geográficos válidos.'; return; }
    this.searchAbort?.abort(); this.searchSequence++; this.buscando = false; this.resultadosBusqueda = [];
    [this.latitudActual, this.longitudActual] = point;
    this.etiquetaPunto = label; this.coordenadasSeleccionadas = true; this.mapaMensaje = '';
    if (recenter) this.mapa.setView(point, Math.max(this.mapa.getZoom(), 17), { animate: true });
    this.colocarMarcador(...point); this.changeDetector.markForCheck();
  }

  async buscarUbicacion(ampliar = false): Promise<void> {
    const q = this.terminoBusqueda.trim();
    if (!q) return;
    const coordinates = parseCoordinates(q);
    if (coordinates) {
      this.searchAbort?.abort(); this.searchSequence++; this.buscando = false; this.resultadosBusqueda = []; this.busquedaAmpliada = false;
      this.seleccionarPunto(...coordinates); return;
    }
    if (this.buscando) return;
    const now = Date.now();
    if (now - this.lastGeocodeAt < 1100) { this.mapaMensaje = 'Espera un momento antes de volver a consultar el buscador geográfico.'; return; }
    this.lastGeocodeAt = now; this.searchAbort?.abort();
    const controller = new AbortController(); this.searchAbort = controller;
    const sequence = ++this.searchSequence; this.buscando = true; this.mapaMensaje = ''; this.resultadosBusqueda = [];
    this.busquedaAmpliada = ampliar;
    const query = ampliar ? q.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/\s+/g, ' ').trim() : normalizedGeocodeQuery(q);
    const params = new URLSearchParams({ format: 'jsonv2', limit: '6', countrycodes: 'pe', q: query });
    if (!ampliar) { params.set('viewbox', '-70.20,-15.34,-70.05,-15.52'); params.set('bounded', '0'); }
    const timeout = window.setTimeout(() => controller.abort(), 12000);
    try {
      const response = await fetch(`https://nominatim.openstreetmap.org/search?${params}`, { headers: { 'Accept-Language': 'es' }, referrerPolicy: 'origin', signal: controller.signal });
      if (!response.ok) throw new Error('geocoding');
      const raw = await response.json() as GeoSuggestion[];
      const valid = raw.filter(item => item && typeof item.display_name === 'string' && validPoint(Number(item.lat), Number(item.lon)));
      this.zone.run(() => {
        if (sequence !== this.searchSequence) return;
        this.resultadosBusqueda = valid;
        if (!valid.length) this.mapaMensaje = 'No se encontraron coincidencias. Prueba con una dirección más completa, una referencia o coordenadas.';
        this.buscando = false; this.changeDetector.markForCheck();
      });
    } catch {
      this.zone.run(() => {
        if (sequence !== this.searchSequence) return;
        this.resultadosBusqueda = [];
        this.mapaMensaje = controller.signal.aborted ? 'La búsqueda se canceló o tardó demasiado. Puedes intentarlo de nuevo.' : 'No se pudo consultar el buscador geográfico. Puedes seleccionar el punto directamente en el mapa.';
        this.buscando = false; this.changeDetector.markForCheck();
      });
    } finally { window.clearTimeout(timeout); }
  }

  seleccionarResultado(result: GeoSuggestion): void {
    const point = validPoint(Number(result.lat), Number(result.lon));
    if (!point) { this.mapaMensaje = 'El resultado no contiene coordenadas válidas.'; return; }
    this.resultadosBusqueda = []; this.seleccionarPunto(point[0], point[1], true, result.display_name);
  }

  guardarUbicacion(): void {
    if (!this.coordenadasSeleccionadas || !validPoint(this.latitudActual, this.longitudActual)) {
      this.mapaMensaje = 'Selecciona un punto en el mapa antes de confirmar la ubicación.'; return;
    }
    const payload = { latitud: this.latitudActual, longitud: this.longitudActual };
    localStorage.setItem('sm_temp_ubicacion_mapa', JSON.stringify(payload));
    if (this.origenRuta !== 'expedientes/nuevo') localStorage.setItem('ubicacionVivienda', JSON.stringify(payload));
    if (this.origenRuta === 'expedientes/nuevo') this.router.navigate(['/expedientes/nuevo']);
    else this.router.navigate(this.codigoSolicitud ? ['/nueva-solicitud', this.codigoSolicitud] : ['/nueva-solicitud']);
  }

  volver(): void {
    if (this.origenRuta === 'expedientes/nuevo') { this.router.navigate(['/expedientes/nuevo']); return; }
    this.router.navigate(this.codigoSolicitud ? ['/nueva-solicitud', this.codigoSolicitud] : ['/nueva-solicitud']);
  }

  ngOnDestroy(): void { this.searchAbort?.abort(); this.mapa?.remove(); }
}
export { MapaComponent as Mapa };

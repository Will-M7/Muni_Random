import { AfterViewInit, ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter, Subscription } from 'rxjs';
import * as L from 'leaflet';
import { AuthService } from '../../services/auth.service';
import { FiscalizadorTarea, VerificacionService } from '../../services/verificacion.service';
import { ResultadoFiscalizacion } from '../../models/solicitud.model';
import { todayPeru } from '../../core/date';
import {UsuariosService} from '../../services/usuarios.service';
import { visitKey,visitColor,visitPin } from '../../core/visit-identity';
import { formatTimeDual } from '../../core/time';

type Coordenadas = [number, number];
type TareaConCoordenadas = FiscalizadorTarea & {latitud:number;longitud:number};

@Component({
  selector: 'app-fiscalizador',
  imports: [CommonModule, FormsModule, RouterLink, RouterLinkActive],
  templateUrl: './fiscalizador.html',
  styleUrls: ['./fiscalizador.css', './fiscalizador-clarification.css'],
})
export class FiscalizadorComponent implements OnInit, AfterViewInit, OnDestroy {
  private routeMap?: ElementRef<HTMLDivElement>;
  @ViewChild('routeMap') set routeMapElement(element:ElementRef<HTMLDivElement>|undefined){this.routeMap=element;if(element)setTimeout(()=>this.actualizarMapa());}

  fecha = todayPeru();
  tareasDia: FiscalizadorTarea[] = [];
  proximas: FiscalizadorTarea[] = [];
  historial: FiscalizadorTarea[] = [];
  aclaraciones: any[] = [];
  respuestasAclaracion: Record<number,string> = {};
  seleccionada: FiscalizadorTarea | null = null;
  ubicacionActual: Coordenadas | null = null;
  ubicacionActualTexto = '';
  ubicacionActualizada = '';
  cargando = false;
  gpsCargando = false;
  guardando = false;
  descargando = false;
  iniciandoProgramacionId: number|null = null;
  jornadaHtmlHabilitada = true;
  error = '';
  gpsMensaje = '';
  reporte: File | null = null;
  resultado: ResultadoFiscalizacion = 'PENDIENTE';
  observaciones = '';
  vista: 'panel' | 'ruta' | 'historial' | 'proximas' | 'detalle' = 'panel';
  historialFecha = '';
  historialResultado = 'TODOS';
  mapa?: L.Map;
  private navigationSubscription?: Subscription;

  constructor(
    private service: VerificacionService,
    private auth: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private cdr: ChangeDetectorRef,
    private usuarios: UsuariosService,
  ) {}

  get sesion() { return this.auth.session; }
  get visitasDeHoy() { return this.tareasDia; }
  get pendientes() { return this.tareasDia.filter(t => t.resultado === 'PENDIENTE'); }
  get realizadas() { return this.tareasDia.filter(t => t.resultado === 'REALIZADA'); }
  get noRealizadas() { return this.tareasDia.filter(t => t.resultado === 'NO_REALIZADA'); }
  get puntosRuta():TareaConCoordenadas[] { return this.pendientes.filter((t):t is TareaConCoordenadas=>this.coordenadasValidas(t)); }
  get sinCoordenadas():FiscalizadorTarea[]{return this.pendientes.filter(t=>!this.coordenadasValidas(t));}
  get visitasMapa():FiscalizadorTarea[]{const unique=new Map<string,FiscalizadorTarea>();for(const t of [...this.rutaOrdenada,...this.sinCoordenadas,...this.tareasDia,...this.proximas])unique.set(visitKey(t),t);return [...unique.values()];}
  get puntosMapa():TareaConCoordenadas[]{return this.visitasMapa.filter((t):t is TareaConCoordenadas=>this.coordenadasValidas(t));}
  numeroVisita(tarea:FiscalizadorTarea){return this.visitasMapa.findIndex(t=>visitKey(t)===visitKey(tarea))+1;}
  colorVisita(tarea:FiscalizadorTarea){return visitColor(this.numeroVisita(tarea));}
  abrirVisita(tarea:FiscalizadorTarea){if(tarea.esExpediente)this.abrirDiligencia(tarea.programacionId);else void this.router.navigate(['/fiscalizador/visita',tarea.codigo]);}
  get distanciaRutaKm() { return this.calcularRuta().distanciaKm; }
  get rutaOrdenada() { return this.calcularRuta().orden; }
  cerrarSesion(): void { this.auth.logout(); void this.router.navigate(['/login']); }

  async ngOnInit(): Promise<void> {
    try{this.jornadaHtmlHabilitada=(await this.usuarios.misPreferencias()).descargarJornadaHtml;}catch{this.jornadaHtmlHabilitada=false;}
    this.actualizarVista(this.router.url);
    this.navigationSubscription = this.router.events.pipe(filter(e => e instanceof NavigationEnd)).subscribe(e => {
      this.actualizarVista((e as NavigationEnd).urlAfterRedirects);
      void this.cargar();
    });
    await this.cargar();
  }

  ngAfterViewInit(): void { this.actualizarMapa(); }
  ngOnDestroy(): void { this.navigationSubscription?.unsubscribe(); this.mapa?.remove(); }

  private actualizarVista(url: string): void {
    this.vista = url.includes('/historial') ? 'historial' : url.includes('/proximas') ? 'proximas' : url.includes('/visita/') ? 'detalle' : url.includes('/ruta') ? 'ruta' : 'panel';
  }

  async cargar(): Promise<void> {
    this.cargando = true; this.error = '';
    try {
      const [dia, proximas, historial, asignaciones, aclaraciones] = await Promise.all([
        this.service.obtenerMisTareas(this.fecha),
        this.service.obtenerMisProximas(),
        this.service.obtenerMiHistorial(this.historialFecha || undefined, this.historialResultado),
        this.service.obtenerMisExpedientesCampo(),
        this.auth.hasCapability('ACLARACION_RESPONDER') ? this.service.misAclaraciones() : Promise.resolve([]),
      ]);
      this.aclaraciones = aclaraciones;
      const tareasExpediente = asignaciones.map(t => ({
        codigo:t.codigo, ciudadano:t.administrado || 'Administrado sin identificar', dni:t.documentoAdministrado || '',
        telefono:t.telefono || '', direccion:t.direccion || '', referencia:t.referencia || '', fecha:t.fecha, hora:t.hora,
        latitud:t.latitud, longitud:t.longitud, documento:'', resultado:(t.estadoProgramacion==='REALIZADA'?'REALIZADA':t.estadoProgramacion==='NO_REALIZADA'?'NO_REALIZADA':'PENDIENTE') as 'PENDIENTE'|'REALIZADA'|'NO_REALIZADA', estadoSolicitud:t.estadoExpediente,
        esExpediente:true, objetoFiscalizacion:t.objetoFiscalizacion, estadoProgramacion:t.estadoProgramacion,
        tipoVisita:t.tipoVisita, origen:t.origen, programacionId:t.programacionId, diligenciaId:t.diligenciaId, estadoDiligencia:t.estadoDiligencia,
      }));
      const solicitudesMigradas = new Set(asignaciones.map(t => t.solicitudCodigo).filter((codigo): codigo is string => !!codigo));
      const tareasLegacy = (tareas: FiscalizadorTarea[]) => tareas.filter(t => !solicitudesMigradas.has(t.codigo));
      const activas=tareasExpediente.filter(t=>['PROGRAMADA','EN_CURSO','VENCIDA'].includes(t.estadoProgramacion||''));
      this.tareasDia = [...tareasLegacy(dia), ...activas.filter(t => t.fecha === this.fecha)].sort((a,b) => a.hora.localeCompare(b.hora));
      this.proximas = [...tareasLegacy(proximas), ...activas.filter(t => t.fecha > todayPeru())].sort((a,b) => a.fecha.localeCompare(b.fecha)||a.hora.localeCompare(b.hora));
      const historialNativo=tareasExpediente.filter(t=>t.resultado!=='PENDIENTE')
        .filter(t=>!this.historialFecha||t.fecha===this.historialFecha)
        .filter(t=>this.historialResultado==='TODOS'||t.resultado===this.historialResultado);
      this.historial = [...tareasLegacy(historial),...historialNativo].sort((a,b)=>b.fecha.localeCompare(a.fecha)||b.hora.localeCompare(a.hora));
      if(this.vista==='detalle'){const code=this.route.snapshot.paramMap.get('codigo');this.seleccionada=[...this.tareasDia,...this.proximas,...this.historial].find(t=>!t.esExpediente&&t.codigo===code)??null;}
      this.cdr.markForCheck();
      setTimeout(() => this.actualizarMapa());
    } catch { this.error = 'No se pudieron cargar tus fiscalizaciones.'; }
    finally { this.cargando = false; this.cdr.markForCheck(); }
  }

  async cambiarFecha(): Promise<void> { await this.cargar(); }
  horaDual(value:string|null|undefined):string{return formatTimeDual(value);}
  async descargarJornada():Promise<void>{this.descargando=true;this.error='';try{const blob=await this.service.descargarJornada(this.fecha);const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=`visitas-pendientes-${this.fecha}.html`;a.click();setTimeout(()=>URL.revokeObjectURL(url),60000);}catch(e:any){this.error=e?.error?.message||'No se pudo descargar la jornada.';}finally{this.descargando=false;this.cdr.markForCheck();}}
  cancelarRespuestaAclaracion(id:number):void{delete this.respuestasAclaracion[id];this.error='';this.cdr.markForCheck();}
  async responderAclaracion(id:number):Promise<void>{const response=this.respuestasAclaracion[id]?.trim();if(!response)return;this.guardando=true;this.error='';try{await this.service.responderAclaracion(id,response);delete this.respuestasAclaracion[id];this.aclaraciones=await this.service.misAclaraciones();}catch(e:any){this.error=e?.error?.message||'No se pudo responder la aclaración.';}finally{this.guardando=false;this.cdr.markForCheck();}}
  async cambiarFiltroHistorial(): Promise<void> { await this.cargar(); }
  abrirDiligencia(programacionId?: number): void { if (programacionId) void this.router.navigate(['/fiscalizador/diligencia', programacionId]); }
  async iniciarDesdeAgenda(tarea:FiscalizadorTarea):Promise<void>{
    if(!tarea.programacionId)return;
    if(tarea.estadoProgramacion==='EN_CURSO'){this.abrirDiligencia(tarea.programacionId);return;}
    if(!tarea.esExpediente||!['PROGRAMADA','VENCIDA'].includes(tarea.estadoProgramacion||'')||tarea.fecha!==todayPeru()){this.abrirDiligencia(tarea.programacionId);return;}
    if(this.iniciandoProgramacionId)return;
    this.iniciandoProgramacionId=tarea.programacionId;this.error='';this.cdr.markForCheck();
    try{await this.service.iniciarDiligencia(tarea.programacionId,{estadoGps:'NO_DISPONIBLE'});this.abrirDiligencia(tarea.programacionId);}
    catch(e:any){this.error=e?.error?.message||'No se pudo iniciar la fiscalización. Comprueba que la visita te corresponda y esté vigente.';}
    finally{this.iniciandoProgramacionId=null;this.cdr.markForCheck();}
  }

  actualizarUbicacion(): void {
    if (!navigator.geolocation) { this.gpsMensaje = 'Este navegador no ofrece ubicación GPS.'; return; }
    this.gpsCargando = true; this.gpsMensaje = 'Solicitando permiso de ubicación…'; this.cdr.markForCheck();
    navigator.geolocation.getCurrentPosition(
      position => {
        this.ubicacionActual = [position.coords.latitude, position.coords.longitude];
        this.ubicacionActualTexto = `${position.coords.latitude.toFixed(6)}, ${position.coords.longitude.toFixed(6)}`;
        this.ubicacionActualizada = new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Lima' }).format(new Date());
        this.gpsMensaje = 'Ubicación actualizada.'; this.gpsCargando = false; this.cdr.markForCheck(); this.actualizarMapa();
      },
      () => { this.gpsCargando = false; this.gpsMensaje = 'No se pudo obtener la ubicación. Puedes continuar sin GPS.'; this.cdr.markForCheck(); },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 },
    );
  }

  abrirDetalle(tarea: FiscalizadorTarea): void { if(this.vista!=='detalle'){this.abrirVisita(tarea);return;}this.seleccionada = tarea; this.resultado = tarea.resultado; this.observaciones = tarea.observaciones || ''; this.reporte = null; this.error = ''; this.cdr.markForCheck(); }
  cerrarDetalle(): void { this.seleccionada = null; this.error = '';void this.router.navigate(['/fiscalizador']); }

  onReporteSeleccionado(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] || null;
    if (!file) return;
    if (file.type !== 'application/pdf' || !file.name.toLowerCase().endsWith('.pdf')) { this.error = 'El reporte debe ser un PDF válido.'; return; }
    if (file.size > 10 * 1024 * 1024) { this.error = 'El reporte no puede superar 10 MB.'; return; }
    this.reporte = file; this.error = '';
  }

  async guardarResultado(): Promise<void> {
    if (!this.seleccionada || this.resultado === 'PENDIENTE') return;
    if (this.resultado === 'NO_REALIZADA' && !this.observaciones.trim()) { this.error = 'Registra el motivo de la visita no realizada.'; return; }
    if (this.resultado === 'REALIZADA' && !this.reporte && !this.seleccionada.reporteNombreOriginal) { this.error = 'Adjunta el reporte PDF de fiscalización.'; return; }
    this.guardando = true; this.error = '';
    try { await this.service.registrarResultado(this.seleccionada.codigo, this.resultado, this.observaciones, this.reporte); this.seleccionada = null; await this.cargar(); }
    catch (e: any) { this.error = e?.error?.message || 'No se pudo guardar el resultado.'; }
    finally { this.guardando = false; this.cdr.markForCheck(); }
  }

  verEnMapa(tarea: FiscalizadorTarea): void {
    this.seleccionada=tarea;const hasCoordinates=this.coordenadasValidas(tarea);
    this.gpsMensaje=hasCoordinates?'':'Esta visita no tiene un punto exacto. Google Maps abrirá la dirección disponible.';
    if(!hasCoordinates)this.abrirDomicilio(tarea);this.cdr.markForCheck();
    setTimeout(()=>{
      this.routeMap?.nativeElement.scrollIntoView({behavior:'smooth',block:'center'});
      this.actualizarMapa();
      if(hasCoordinates){const point:Coordenadas=[tarea.latitud,tarea.longitud];this.mapa?.setView(point,16);this.abrirPopupTarea(tarea);}
    },0);
  }
  abrirGoogleMaps(tarea: FiscalizadorTarea): void { if(!this.coordenadasValidas(tarea)||!this.ubicacionActual){this.abrirDomicilio(tarea);return;}const origen=this.ubicacionActual;const url=`https://www.google.com/maps/dir/?api=1&origin=${origen[0]},${origen[1]}&destination=${tarea.latitud},${tarea.longitud}&travelmode=driving`;window.open(url,'_blank','noopener'); }
  abrirDomicilio(tarea: FiscalizadorTarea): void { const destino=this.coordenadasValidas(tarea)?`${tarea.latitud},${tarea.longitud}`:tarea.direccion;if(!destino){this.gpsMensaje='Esta visita no tiene ubicación suficiente.';this.cdr.markForCheck();return;}window.open(`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(destino)}`, '_blank', 'noopener'); }
  abrirRecorridoCompleto(): void { const orden=this.rutaOrdenada;if(!orden.length)return;if(orden.length===1&&!this.ubicacionActual){this.abrirDomicilio(orden[0]);return;}const origen=this.ubicacionActual||[orden[0].latitud,orden[0].longitud];const stops=this.ubicacionActual?orden:orden.slice(1);const destino=stops.at(-1)!;const waypoints=stops.slice(0,-1).map(t=>`${t.latitud},${t.longitud}`).join('|');const url=`https://www.google.com/maps/dir/?api=1&origin=${origen[0]},${origen[1]}&destination=${destino.latitud},${destino.longitud}${waypoints?`&waypoints=${encodeURIComponent(waypoints)}`:''}&travelmode=driving`;window.open(url,'_blank','noopener'); }

  private calcularRuta(): { orden: TareaConCoordenadas[]; distanciaKm: number } {
    const pendientes = [...this.puntosRuta]; if (!pendientes.length) return { orden: [], distanciaKm: 0 };
    let actual:Coordenadas|null=this.ubicacionActual; let distancia = 0; const orden: TareaConCoordenadas[] = [];
    while (pendientes.length) { pendientes.sort((a,b)=>this.ubicacionActual
      ?this.distancia(actual!,[a.latitud,a.longitud])-this.distancia(actual!,[b.latitud,b.longitud])||a.hora.localeCompare(b.hora)
      :a.hora.localeCompare(b.hora)||(actual?this.distancia(actual,[a.latitud,a.longitud])-this.distancia(actual,[b.latitud,b.longitud]):a.codigo.localeCompare(b.codigo)));
      const siguiente = pendientes.shift()!;if(actual)distancia += this.distancia(actual, [siguiente.latitud, siguiente.longitud]); orden.push(siguiente); actual = [siguiente.latitud, siguiente.longitud]; }
    return { orden, distanciaKm: Number(distancia.toFixed(2)) };
  }
  private distancia(a: Coordenadas, b: Coordenadas): number { const r = 6371; const dLat = (b[0] - a[0]) * Math.PI / 180; const dLon = (b[1] - a[1]) * Math.PI / 180; const x = Math.sin(dLat / 2) ** 2 + Math.cos(a[0] * Math.PI / 180) * Math.cos(b[0] * Math.PI / 180) * Math.sin(dLon / 2) ** 2; return r * 2 * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x)); }
  private coordenadasValidas(t:FiscalizadorTarea):t is TareaConCoordenadas{return t.latitud!==null&&t.longitud!==null&&Number.isFinite(t.latitud)&&Number.isFinite(t.longitud)&&t.latitud!==0&&t.longitud!==0&&Math.abs(t.latitud)<=90&&Math.abs(t.longitud)<=180;}

  private actualizarMapa(): void {
    if (!this.routeMap?.nativeElement) return;
    const centro = this.ubicacionActual || (this.puntosMapa.length ? [this.puntosMapa[0].latitud, this.puntosMapa[0].longitud] as Coordenadas : null);
    const initialCenter:Coordenadas=centro??[-15.3977773,-70.1217652];
    if (!this.mapa) { this.mapa = L.map(this.routeMap.nativeElement).setView(initialCenter, 14); L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: '© OpenStreetMap contributors' }).addTo(this.mapa); }
    this.mapa.eachLayer(layer => { if (layer instanceof L.Marker) this.mapa?.removeLayer(layer); });
    const markers: L.LatLngExpression[] = [];
    if (this.ubicacionActual) { L.marker(this.ubicacionActual, { icon: L.divIcon({ className: 'route-marker current-marker', html: '<span>0</span>', iconSize: [30, 30], iconAnchor: [15, 15] }) }).addTo(this.mapa).bindPopup('Ubicación actual'); markers.push(this.ubicacionActual); }
    this.puntosMapa.forEach((tarea, index) => { const point: L.LatLngExpression = [tarea.latitud, tarea.longitud]; const popup=document.createElement('div');const summary=document.createElement('strong');summary.textContent=`${tarea.ciudadano} · ${tarea.codigo}`;const detail=document.createElement('p');detail.textContent=`${tarea.direccion||'Sin dirección'} · ${tarea.fecha} ${tarea.hora.slice(0,5)}`;popup.append(summary,detail);if(tarea.telefono){const phone=document.createElement('p');phone.textContent=`Teléfono: ${tarea.telefono}`;popup.append(phone);}const link=document.createElement('a');link.href=`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(`${tarea.latitud},${tarea.longitud}`)}`;link.target='_blank';link.rel='noopener';link.textContent='Navegar con Google Maps';popup.append(link);const access=document.createElement('button');access.type='button';access.textContent='Consultar visita';access.addEventListener('click',()=>this.abrirVisita(tarea));popup.append(access);const number=this.numeroVisita(tarea);const color=this.colorVisita(tarea);const html=visitPin(number,color);L.marker(point,{icon:L.divIcon({className:'route-marker visit-pin',html,iconSize:[36,44],iconAnchor:[18,43],popupAnchor:[0,-39]})}).addTo(this.mapa!).bindPopup(popup);markers.push(point); });
    if (markers.length > 1) this.mapa.fitBounds(L.latLngBounds(markers), { padding: [20, 20], maxZoom: 16 });
    else if(markers.length===1)this.mapa.setView(markers[0],15);
    this.mapa.invalidateSize({ pan: false });
  }

  private abrirPopupTarea(tarea: TareaConCoordenadas): void {
    this.mapa?.eachLayer(layer => {
      if (!(layer instanceof L.Marker) || !layer.getPopup()) return;
      const contenido = layer.getPopup()?.getContent();
      const punto = layer.getLatLng();
      if (contenido instanceof HTMLElement && contenido.textContent?.includes(tarea.codigo) && punto.lat === tarea.latitud && punto.lng === tarea.longitud) layer.openPopup();
    });
  }

  async abrirDocumento(): Promise<void> { if (!this.seleccionada) return; const blob = await this.service.abrirDocumentoTarea(this.seleccionada.codigo); this.abrirBlob(blob); }
  async abrirReporte(): Promise<void> { if (!this.seleccionada) return; const blob = await this.service.abrirReporteTarea(this.seleccionada.codigo); this.abrirBlob(blob); }
  private abrirBlob(blob: Blob): void { const url = URL.createObjectURL(blob); window.open(url, '_blank', 'noopener'); setTimeout(() => URL.revokeObjectURL(url), 60000); }
}
export { FiscalizadorComponent as Fiscalizador };

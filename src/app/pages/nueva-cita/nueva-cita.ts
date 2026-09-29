import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  EstadoSolicitud,
  Solicitud,
} from '../../models/solicitud.model';
import { VerificacionService } from '../../services/verificacion.service';
import { ReniecService } from '../../services/reniec.service';
import { todayPeru } from '../../core/date';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, EMPTY, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, filter, switchMap, takeUntil, tap } from 'rxjs/operators';
import { ProgramacionDia } from '../../services/verificacion.service';
import { Fiscalizador } from '../../models/solicitud.model';

@Component({
  selector: 'app-nueva-cita',
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './nueva-cita.html',
  styleUrls: ['./nueva-cita.css', './agenda.css'],
})
export class NuevaCitaComponent implements OnInit, OnDestroy {
  esModoEdicion = false;
  codigoEdicion = '';

  // Formulario
  datos = {
    dni: '',
    nombres: '',
    apellidos: '',
    telefono: '',
    direccion: '',
    referencia: '',
    documento: '',
    documentoTamano: '',
    latitud: null as number | null,
    longitud: null as number | null,
    ubicacion: '',
    fecha: todayPeru(),
    hora: '09:00 a. m.',
    fiscalizadorId: '',
    estado: 'En espera' as EstadoSolicitud,
    observaciones: '',
    fechaFiscalizacion: '',
    horaFiscalizacion: '',
  };

  ubicacionSeleccionadaEnMapa = false;
  archivoInvalido = false;
  mensajeError = '';
  mensajeExito = '';
  guardando = false;
  archivoSeleccionado: File | null = null;
  agendaDelDia: ProgramacionDia[] = [];
  cargandoAgenda = false;
  agendaError = '';
  horarioOcupado = false;
  fiscalizadores: Fiscalizador[] = [];

  // RENIEC Lookup
  cargandoDni = false;
  dniConsultadoExito = false;
  dniMensaje = '';
  private readonly dniInput$ = new Subject<string>();
  private readonly destroy$ = new Subject<void>();
  private readonly fechaFiscalizacion$ = new Subject<string>();
  private dniAnterior = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private verificacionService: VerificacionService,
    private reniecService: ReniecService,
    private changeDetector: ChangeDetectorRef
  ) {}

  async ngOnInit(): Promise<void> {
    this.dniInput$.pipe(
      tap(() => { this.cargandoDni = false; this.dniMensaje = ''; this.dniConsultadoExito = false; }),
      debounceTime(500),
      distinctUntilChanged(),
      filter(dni => dni.length === 8),
      tap(() => { this.cargandoDni = true; this.dniMensaje = 'Consultando RENIEC…'; }),
      switchMap(dni => this.reniecService.consultarDni(dni).pipe(
        tap(res => {
          this.cargandoDni = false;
          this.datos.nombres = res.nombres;
          this.datos.apellidos = [res.apellidoPaterno, res.apellidoMaterno].filter(Boolean).join(' ');
          this.dniConsultadoExito = true;
          this.dniMensaje = 'Datos validados con RENIEC.';
          this.changeDetector.markForCheck();
        }),
        catchError((error: HttpErrorResponse) => {
          this.cargandoDni = false;
          this.dniConsultadoExito = false;
          this.dniMensaje = error.status === 404 ? 'DNI no encontrado en RENIEC. Complete los datos manualmente.' : error.status === 503 ? 'Consulta RENIEC no disponible. Complete los datos manualmente.' : 'No fue posible consultar RENIEC en este momento. Complete los datos manualmente.';
          this.changeDetector.markForCheck();
          return EMPTY;
        })
      )),
      takeUntil(this.destroy$)
    ).subscribe({ complete: () => { this.cargandoDni = false; } });
    this.fechaFiscalizacion$.pipe(
      debounceTime(200), distinctUntilChanged(),
      tap(fecha => { this.cargandoAgenda = !!fecha; this.agendaError = ''; if (!fecha) this.agendaDelDia = []; }),
      switchMap(fecha => fecha ? this.verificacionService.programacion$(fecha).pipe(catchError(() => { this.agendaError = 'No se pudo cargar la programación del día.'; return of([]); })) : of([])),
      takeUntil(this.destroy$)
    ).subscribe(agenda => { this.agendaDelDia = agenda; this.cargandoAgenda = false; this.actualizarConflictoHorario(); this.changeDetector.markForCheck(); });
    await this.verificacionService.obtenerSolicitudes();
    this.fiscalizadores = this.verificacionService.obtenerFiscalizadores
      ? await this.verificacionService.obtenerFiscalizadores()
      : [];

    // Detectar si venimos en modo edición
    const codigoParam =
      this.route.snapshot.paramMap.get('codigo') ||
      this.route.snapshot.queryParamMap.get('edit');

    if (codigoParam) {
      this.iniciarModoEdicion(codigoParam);
    } else {
      this.restaurarBorradorOInicial();
    }

    // Verificar si venimos del mapa con nueva ubicación
    this.comprobarRetornoDeMapa();
    this.changeDetector.markForCheck();

  }

  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }

  private iniciarModoEdicion(codigo: string): void {
    const solicitud = this.verificacionService.obtenerSolicitudPorCodigo(codigo);
    if (solicitud) {
      this.esModoEdicion = true;
      this.codigoEdicion = solicitud.codigo;
      this.datos = {
        dni: solicitud.dni,
        nombres: solicitud.nombres || solicitud.nombre.split(' ')[0] || '',
        apellidos: solicitud.apellidos || solicitud.nombre.split(' ').slice(1).join(' ') || '',
        telefono: solicitud.telefono,
        direccion: solicitud.direccion,
        referencia: solicitud.referencia || '',
        documento: solicitud.documento || '',
        documentoTamano: solicitud.documentoTamano || '1.0 MB',
        latitud: solicitud.latitud,
        longitud: solicitud.longitud,
        ubicacion: solicitud.ubicacion,
        fecha: solicitud.fecha,
        hora: solicitud.hora,
        fechaFiscalizacion: solicitud.fechaFiscalizacion || '',
        horaFiscalizacion: solicitud.horaFiscalizacion || '',
        fiscalizadorId: solicitud.fiscalizadorId || '',
        estado: solicitud.estado,
        observaciones: solicitud.observaciones || '',
      };
      this.ubicacionSeleccionadaEnMapa = true;
      this.onFechaFiscalizacionChange(this.datos.fechaFiscalizacion);
    }
  }

  private restaurarBorradorOInicial(): void {
    const draft = localStorage.getItem('sm_draft_nueva_solicitud');
    if (draft) {
      try {
        const parsed = JSON.parse(draft);
        const { ubicacionConfirmada, ...campos } = parsed;
        this.datos = { ...this.datos, ...campos };
        this.ubicacionSeleccionadaEnMapa = ubicacionConfirmada === true
          && parsed.latitud != null && parsed.longitud != null
          && Number.isFinite(Number(parsed.latitud)) && Number.isFinite(Number(parsed.longitud));
      } catch {
        // Ignorar
      }
    }
  }

  private comprobarRetornoDeMapa(): void {
    const seleccionPendiente = localStorage.getItem('sm_location_selection_pending') === '1';
    localStorage.removeItem('sm_location_selection_pending');
    const tempCoords = localStorage.getItem('sm_temp_ubicacion_mapa');
    if (seleccionPendiente && tempCoords) {
      try {
        const coords = JSON.parse(tempCoords);
        if (coords.latitud && coords.longitud) {
          this.datos.latitud = coords.latitud;
          this.datos.longitud = coords.longitud;
          this.datos.ubicacion = `${coords.latitud.toFixed(6)}, ${coords.longitud.toFixed(6)}`;
          this.ubicacionSeleccionadaEnMapa = true;
        }
      } catch {
        // Ignorar
      }
      localStorage.removeItem('sm_temp_ubicacion_mapa');
    }
  }


  // Consulta automática y manual de DNI ante RENIEC
  onDniInput(): void {
    this.datos.dni = (this.datos.dni || '').replace(/\D/g, '').slice(0, 8);
    if (this.dniAnterior.length === 8 && this.dniAnterior !== this.datos.dni) {
      this.datos.nombres = '';
      this.datos.apellidos = '';
      this.dniConsultadoExito = false;
    }
    this.dniAnterior = this.datos.dni;
    if (this.datos.dni.length < 8) { this.dniConsultadoExito = false; this.dniMensaje = ''; }
    this.dniInput$.next(this.datos.dni);
  }

  onFechaFiscalizacionChange(fecha: string): void { this.datos.fechaFiscalizacion = fecha || ''; this.fechaFiscalizacion$.next(this.datos.fechaFiscalizacion); }
  onHoraFiscalizacionChange(hora: string): void { this.datos.horaFiscalizacion = hora || ''; this.actualizarConflictoHorario(); }
  private actualizarConflictoHorario(): void { const hora = (this.datos.horaFiscalizacion || '').slice(0, 5); const fiscalizador=this.datos.fiscalizadorId; this.horarioOcupado = !!hora && !!fiscalizador && this.agendaDelDia.some(item => item.horaFiscalizacion.slice(0, 5) === hora && item.fiscalizador === this.fiscalizadores.find(f=>f.id===fiscalizador)?.nombre && item.codigo !== this.codigoEdicion); }

  // Documento PDF
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) return;

    const file = input.files[0];
    this.archivoInvalido = false;

    // Validación estricta de PDF
    const esPdf =
      file.type === 'application/pdf' ||
      file.name.toLowerCase().endsWith('.pdf');

    if (!esPdf) {
      this.archivoInvalido = true;
      this.mensajeError =
        'Error: El documento debe ser obligatoriamente un archivo en formato PDF (.pdf).';
      input.value = '';
      return;
    }

    const kb = Math.round(file.size / 1024);
    const tamanoStr = kb > 1024 ? `${(kb / 1024).toFixed(1)} MB` : `${kb} KB`;

    if (file.size > 10 * 1024 * 1024) { this.archivoInvalido = true; this.mensajeError = 'El PDF no puede superar 10 MB.'; input.value = ''; return; }
    this.archivoSeleccionado = file;
    this.verificacionService.guardarArchivoPendiente(file);
    this.datos.documento = file.name;
    this.datos.documentoTamano = tamanoStr;
    this.mensajeError = '';
  }

  eliminarDocumento(): void {
    this.datos.documento = '';
    this.datos.documentoTamano = '';
    this.archivoSeleccionado = null;
    this.verificacionService.limpiarArchivoPendiente();
  }

  // Google Maps abre la dirección escrita; las coordenadas se confirman por separado.
  abrirMapa(): void {
    const direccion = this.datos.direccion.trim();
    if (!direccion) {
      this.mensajeError = 'Ingresa la dirección exacta de San Miguel antes de abrir Google Maps.';
      return;
    }
    this.mensajeError = '';
    window.open(`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(direccion)}`, '_blank', 'noopener,noreferrer');
  }

  abrirSelectorCoordenadas(): void {
    this.guardarBorrador();
    localStorage.removeItem('sm_temp_ubicacion_mapa');
    localStorage.setItem('sm_location_selection_pending', '1');
    if (this.datos.latitud != null && this.datos.longitud != null) {
      localStorage.setItem('ubicacionVivienda', JSON.stringify({ latitud: this.datos.latitud, longitud: this.datos.longitud }));
    } else {
      localStorage.removeItem('ubicacionVivienda');
    }
    this.router.navigate(['/mapa'], {
      queryParams: this.esModoEdicion
        ? { retorno: 'nueva-solicitud', edit: this.codigoEdicion }
        : { retorno: 'nueva-solicitud' },
    });
  }

  private guardarBorrador(): void {
    localStorage.setItem(
      'sm_draft_nueva_solicitud',
      JSON.stringify({ ...this.datos, ubicacionConfirmada: this.ubicacionSeleccionadaEnMapa })
    );
  }

  // Guardar Solicitud
  async guardarSolicitud(): Promise<void> {
    this.mensajeError = '';
    if (this.guardando) return;

    // Validaciones de datos del solicitante
    if (!this.datos.dni || this.datos.dni.trim().length !== 8) {
      this.mensajeError = 'El DNI debe contener exactamente 8 dígitos numéricos.';
      return;
    }

    if (!this.datos.nombres.trim() || !this.datos.apellidos.trim()) {
      this.mensajeError = 'Debe ingresar los nombres y apellidos completos del ciudadano.';
      return;
    }

    if (!this.datos.telefono.trim()) {
      this.mensajeError = 'Debe ingresar un número de teléfono o celular de contacto.';
      return;
    }

    if (!this.datos.direccion.trim()) {
      this.mensajeError = 'Debe ingresar la dirección completa de la vivienda en San Miguel.';
      return;
    }

    // Validación de documento
    if (!this.datos.documento) {
      this.mensajeError = 'Debe adjuntar el documento de sustento en formato PDF.';
      return;
    }

    // Validación de ubicación
    if (!this.ubicacionSeleccionadaEnMapa || this.datos.latitud == null || this.datos.longitud == null) {
      this.mensajeError = 'Debe marcar la ubicación del domicilio para asociar coordenadas al expediente.';
      return;
    }

    if (!/^\d{4}-\d{2}-\d{2}$/.test(this.datos.fechaFiscalizacion) || !this.datos.fechaFiscalizacion || !/^\d{2}:\d{2}$/.test(this.datos.horaFiscalizacion)) {
      this.mensajeError = 'Debe indicar una fecha y hora válidas para la fiscalización.';
      return;
    }
    if (this.horarioOcupado) { this.mensajeError = 'Ya existe una fiscalización programada a esta hora.'; return; }

    this.guardando = true;

    const payload: Partial<Solicitud> = {
      nombres: this.datos.nombres.trim(),
      apellidos: this.datos.apellidos.trim(),
      nombre: `${this.datos.nombres.trim()} ${this.datos.apellidos.trim()}`,
      dni: this.datos.dni.trim(),
      telefono: this.datos.telefono.trim(),
      direccion: this.datos.direccion.trim(),
      referencia: this.datos.referencia.trim(),
      documento: this.datos.documento,
      documentoTamano: this.datos.documentoTamano || '1.2 MB',
      latitud: this.datos.latitud,
      longitud: this.datos.longitud,
      ubicacion: `${this.datos.latitud.toFixed(6)}, ${this.datos.longitud.toFixed(6)}`,
      fecha: this.datos.fecha,
      hora: this.datos.hora,
      fiscalizadorId: this.datos.fiscalizadorId,
      fiscalizadorNombre: '',
      estado: this.datos.estado,
      observaciones: this.datos.observaciones.trim(),
      fechaFiscalizacion: this.datos.fechaFiscalizacion,
      horaFiscalizacion: this.datos.horaFiscalizacion,
    };

    if (this.esModoEdicion) {
      payload.codigo = this.codigoEdicion;
    }

    try {
      const resultado = await this.verificacionService.guardarSolicitud(payload, this.archivoSeleccionado || this.verificacionService.obtenerArchivoPendiente());
      this.verificacionService.limpiarArchivoPendiente();
      localStorage.removeItem('sm_draft_nueva_solicitud');
      this.mensajeExito = this.esModoEdicion
        ? `Solicitud ${resultado.codigo} actualizada con éxito.`
        : `Solicitud registrada con éxito. Código generado: ${resultado.codigo}`;
      await this.router.navigate(['/solicitudes']);
    } catch (error: any) {
      this.mensajeError = error?.error?.message || 'No se pudo guardar la solicitud. Verifica los datos e inténtalo nuevamente.';
      this.guardando = false;
    }
  }

  cancelar(): void {
    localStorage.removeItem('sm_draft_nueva_solicitud');
    this.router.navigate(['/solicitudes']);
  }
}
export { NuevaCitaComponent as NuevaCita };

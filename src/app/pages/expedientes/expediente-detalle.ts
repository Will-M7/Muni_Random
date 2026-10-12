import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ActaFiscalizacion, AgendaVisita, Expediente, ExpedienteDocumento, HistorialExpediente, ProgramacionExpediente, VerificacionService } from '../../services/verificacion.service';
import { Fiscalizador } from '../../models/solicitud.model';
import { formatTimeDual, validTime24 } from '../../core/time';
import { formatLimaLocalDateTime } from '../../core/date';
import { UsuariosService } from '../../services/usuarios.service';
import { AuthService } from '../../services/auth.service';

@Component({selector:'app-expediente-detalle',standalone:true,imports:[CommonModule,FormsModule,RouterLink],templateUrl:'./expediente-detalle.html',styleUrls:['./expedientes.css','./expediente-detalle.css']})
export class ExpedienteDetalleComponent implements OnInit {
  expediente:Expediente|null=null; documentos:ExpedienteDocumento[]=[]; actas:ActaFiscalizacion[]=[]; ciclos:any[]=[]; fiscalizadores:Fiscalizador[]=[]; cargando=true; error=''; accion:''|'programar'|'reprogramar'|'cancelar'=''; programacion:ProgramacionExpediente|null=null;
  historialCompleto=false;
  fecha='';hora='09:00';fiscalizadorId='';tipoVisita='PROGRAMADA';motivo='';
  private users=inject(UsuariosService);limiteDiario=5;sobrecargaConfirmada=false;agenda:AgendaVisita[]=[];agendaError='';archivos:File[]=[];adjuntando=false;actaArchivos:File[]=[];adjuntandoActas=false;avisoDocumentos='';
  private auth=inject(AuthService);
  constructor(private route:ActivatedRoute,private router:Router,private api:VerificacionService,private cdr:ChangeDetectorRef){}
  async ngOnInit(){this.limiteDiario=await this.users.limiteDiario().catch(()=>5);await this.cargar();try{this.fiscalizadores=await this.api.obtenerFiscalizadores();}catch{}}
  async cargar(){this.cargando=true;try{this.expediente=await this.api.obtenerExpediente(this.route.snapshot.paramMap.get('codigo')||'');const [actas,ciclos,documentos]=await Promise.all([this.api.actasExpediente(this.expediente.codigo),this.api.ciclosFiscalizacion(this.expediente.codigo),this.api.documentosExpediente(this.expediente.codigo)]);this.actas=actas;this.ciclos=ciclos.ciclos||[];this.documentos=documentos;}catch{this.error='No se pudo cargar el expediente, actas o documentos.';}finally{this.cargando=false;this.cdr.markForCheck();}}
  get historialOrdenado():HistorialExpediente[]{return [...(this.expediente?.historial||[])].sort((a,b)=>b.fechaHora.localeCompare(a.fechaHora));}
  get historialVisible():HistorialExpediente[]{const items=this.historialOrdenado;return this.historialCompleto?items:items.slice(0,5);}
  origen(o:string){return ({SOLICITUD:'Solicitud',DENUNCIA:'Denuncia',INICIATIVA_MUNICIPAL:'Iniciativa municipal',ORDEN_SUPERIOR:'Orden superior',OPERATIVO_PROGRAMADO:'Operativo programado',OPERATIVO_INOPINADO:'Operativo inopinado',OTRO:'Otro'} as Record<string,string>)[o]||o;}
  horaDual(value:string|null){return formatTimeDual(value);}
  fechaLima(value:string|null|undefined){return formatLimaLocalDateTime(value);}
  fechaHistorial(value:string|null|undefined):string{const formatted=formatLimaLocalDateTime(value);return formatted==='—'?'Fecha no disponible':formatted;}
  tipoArchivo(nombre:string,mime:string){const ext=nombre.toLowerCase().split('.').pop()||'';return ['pdf','doc','docx','jpg','jpeg','png'].includes(ext)?ext.toUpperCase():mime;}
  get documentosGenerales(){return this.documentos.filter(d=>d.categoria!=='ACTA_ADJUNTA');}
  get actasAdjuntas(){return this.documentos.filter(d=>d.categoria==='ACTA_ADJUNTA');}
  get puedeAdjuntar(){return this.auth.hasCapability('EXPEDIENTE_EDITAR')&&this.expediente?.estado!=='CERRADO'&&this.expediente?.estado!=='CANCELADO';}
  async cargarAgenda(){this.sobrecargaConfirmada=false;this.agenda=[];this.agendaError='';if(!this.fecha)return;try{this.agenda=await this.api.agendaProgramacion(this.fecha);}catch{this.agendaError='No se pudo consultar la agenda.';}this.cdr.markForCheck();}
  get cargaValida(){return this.agenda.filter(v=>!['CANCELADA','REPROGRAMADA'].includes(v.estado)).length;}
  totalFiscalizador(id:string){return this.agenda.filter(v=>v.fiscalizadorId===id&&!['CANCELADA','REPROGRAMADA'].includes(v.estado)).length;}
  conflicto(v:AgendaVisita){return v.fiscalizadorId===this.fiscalizadorId&&v.hora?.slice(0,5)===this.hora&&!['CANCELADA','REPROGRAMADA'].includes(v.estado);}
  seleccionarDocumentos(event:Event){const input=event.target as HTMLInputElement,files=Array.from(input.files||[]);input.value='';this.error='';if(this.archivos.length+files.length>10){this.error='Puedes adjuntar como máximo 10 documentos por envío.';return;}const invalid=files.find(f=>!['jpg','jpeg','png','pdf','doc','docx'].includes(f.name.toLowerCase().split('.').pop()||''));if(invalid){this.error='Formato no permitido: '+invalid.name+'. Usa PDF, JPG, JPEG, PNG, DOC o DOCX.';return;}const tooLarge=files.find(f=>f.size>10*1024*1024);if(tooLarge){this.error='El archivo '+tooLarge.name+' supera el máximo de 10 MB.';return;}this.archivos=[...this.archivos,...files];}
  quitarDocumento(index:number){this.archivos=this.archivos.filter((_,i)=>i!==index);}
  async adjuntarDocumentos(){if(!this.expediente||!this.archivos.length)return;this.adjuntando=true;this.error='';this.avisoDocumentos='';try{this.documentos=await this.api.adjuntarDocumentosExpediente(this.expediente.codigo,this.archivos);this.archivos=[];this.avisoDocumentos='Documentos adjuntados correctamente.';await this.actualizarHistorial();}catch{this.error='No se pudieron adjuntar los documentos. Revisa tipos, tamaño y permisos.';}finally{this.adjuntando=false;this.cdr.markForCheck();}}
  seleccionarActas(event:Event){const input=event.target as HTMLInputElement,files=Array.from(input.files||[]);input.value='';this.error='';if(this.actaArchivos.length+files.length>10){this.error='Puedes adjuntar hasta 10 actas PDF por envío.';return;}if(files.some(f=>!f.name.toLowerCase().endsWith('.pdf')||f.size>10*1024*1024)){this.error='Cada acta adjunta debe ser PDF de hasta 10 MB.';return;}this.actaArchivos=[...this.actaArchivos,...files];}
  quitarActa(index:number){this.actaArchivos=this.actaArchivos.filter((_,i)=>i!==index);}
  async adjuntarActas(){if(!this.expediente||!this.actaArchivos.length)return;this.adjuntandoActas=true;this.error='';this.avisoDocumentos='';try{this.documentos=await this.api.adjuntarActasExpediente(this.expediente.codigo,this.actaArchivos);this.actaArchivos=[];this.avisoDocumentos='Acta adjunta guardada correctamente.';await this.actualizarHistorial();}catch{this.error='No se pudieron adjuntar las actas. Comprueba que sean PDF válidos y el expediente esté abierto.';}finally{this.adjuntandoActas=false;this.cdr.markForCheck();}}
  private async actualizarHistorial(){if(!this.expediente)return;try{this.expediente=await this.api.obtenerExpediente(this.expediente.codigo);}catch{this.avisoDocumentos+=' Actualiza la página para ver el nuevo evento en el historial.';}}
  vigente(){return this.expediente?[...this.expediente.programaciones].reverse().find(p=>p.estado!=='REPROGRAMADA')??null:null;}
  abrir(a:'reprogramar'|'cancelar',p:ProgramacionExpediente){this.sobrecargaConfirmada=false;this.accion=a;this.programacion=p;this.fecha='';this.hora=p.hora||'09:00';this.fiscalizadorId=p.fiscalizadorId||'';this.tipoVisita=p.tipoVisita;this.motivo='';this.error='';}
  abrirProgramacion(){this.sobrecargaConfirmada=false;this.accion='programar';this.programacion=null;this.fecha='';this.hora='09:00';this.fiscalizadorId='';this.tipoVisita='PROGRAMADA';this.error='';}
  cerrar(){this.accion='';this.programacion=null;}
  puedeModificar(p:ProgramacionExpediente){return this.expediente?.estado!=='CERRADO'&&['PROGRAMADA','VENCIDA'].includes(p.estado);}
  async revisar(d:any){try{const r=await this.api.iniciarRevision(d.diligenciaId);await this.router.navigate(['/revisiones',r.revision.id]);}catch{this.error='No se pudo abrir la revisión de esta diligencia.';}this.cdr.markForCheck();}
  async guardar(){if(this.accion!=='programar'&&(!this.programacion||!this.motivo.trim())){this.error='El motivo es obligatorio.';return;}if(this.accion!=='cancelar'&&(!this.fecha||!validTime24(this.hora)||!this.fiscalizadorId)){this.error='Indica fecha, hora HH:mm y fiscalizador.';return;}if(this.accion!=='cancelar'){if(!this.agenda.length)await this.cargarAgenda();const count=this.agenda.filter(v=>!['CANCELADA','REPROGRAMADA'].includes(v.estado)).length+(this.accion==='reprogramar'&&this.programacion?.fecha===this.fecha?0:1);if(count>this.limiteDiario&&!this.sobrecargaConfirmada){this.sobrecargaConfirmada=true;this.error='La agenda superará el límite orientativo de '+this.limiteDiario+' visitas. Pulsa Guardar otra vez para continuar.';return;}}try{if(this.accion==='programar'&&this.expediente){await this.api.programarExpediente(this.expediente.codigo,{fecha:this.fecha,hora:this.hora,fiscalizadorId:this.fiscalizadorId,tipoVisita:this.tipoVisita});}else if(this.accion==='reprogramar'&&this.programacion){await this.api.reprogramar(this.programacion.id,{fecha:this.fecha,hora:this.hora,motivo:this.motivo,fiscalizadorId:this.fiscalizadorId,tipoVisita:this.tipoVisita});}else if(this.accion==='cancelar'&&this.programacion){await this.api.cancelarProgramacion(this.programacion.id,this.motivo);}this.cerrar();await this.cargar();}catch{this.error='No se pudo completar la operación. Verifica fecha, disponibilidad y permisos.';}this.cdr.markForCheck();}
  async descargarDocumento(d:ExpedienteDocumento){if(!this.expediente)return;const viewer=d.tipoMime==='application/pdf'?window.open('about:blank','_blank'):null;if(viewer)viewer.opener=null;try{const blob=await this.api.descargarDocumentoExpediente(this.expediente.codigo,d.id);if(d.tipoMime==='application/pdf'){const url=URL.createObjectURL(new Blob([blob],{type:'application/pdf'}));if(viewer)viewer.location.href=url;else this.error='El navegador bloqueó la pestaña del PDF. Permite ventanas emergentes e inténtalo de nuevo.';setTimeout(()=>URL.revokeObjectURL(url),60000);}else this.abrirBlob(blob,d.nombre);}catch{viewer?.close();this.error='No se pudo abrir el documento.';}}
  async documento(){if(!this.expediente)return;const viewer=window.open('about:blank','_blank');if(viewer)viewer.opener=null;try{const blob=await this.api.abrirSustento(this.expediente.codigo);const url=URL.createObjectURL(new Blob([blob],{type:'application/pdf'}));if(viewer)viewer.location.href=url;else this.error='El navegador bloqueó la pestaña del PDF.';setTimeout(()=>URL.revokeObjectURL(url),60000);}catch{viewer?.close();this.error='No se pudo abrir el documento de sustento.';}}
  async reporte(){if(!this.expediente?.solicitudCodigo)return;try{const blob=await this.api.abrirReporte(this.expediente.solicitudCodigo);window.open(URL.createObjectURL(blob),'_blank','noopener');}catch{this.error='No se pudo abrir el reporte del fiscalizador.';}}
  async descargarActa(acta:ActaFiscalizacion){try{const blob=await this.api.descargarActa(acta.id);this.abrirBlob(blob,acta.pdfGeneradoNombre);}catch{this.error='No se pudo descargar el acta generada.';}}
  async descargarCopiaFirmada(acta:ActaFiscalizacion){try{const blob=await this.api.descargarCopiaFirmadaActa(acta.id);this.abrirBlob(blob,acta.pdfFirmadoNombre||'Acta-firmada.pdf');}catch{this.error='No se pudo descargar la copia firmada.';}}
  private abrirBlob(blob:Blob,nombre:string|null){const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=nombre||'Acta.pdf';a.click();setTimeout(()=>URL.revokeObjectURL(url),60000);}
  nombreEvento(code:string){return ({
    EXPEDIENTE_CREADO:'Expediente creado',EXPEDIENTE_EDITADO:'Datos del expediente actualizados',EXPEDIENTE_CERRADO:'Expediente cerrado',
    PROGRAMACION_CREADA:'Visita programada',PROGRAMACION_REPROGRAMADA:'Visita reprogramada',PROGRAMACION_CREADA_POR_REPROGRAMACION:'Nueva fecha programada',
    PROGRAMACION_CANCELADA:'Visita cancelada',PROGRAMACION_VENCIDA:'Visita vencida',PROGRAMACION_SINCRONIZADA:'Visita sincronizada',
    PROGRAMACION_SEGUIMIENTO_CREADA:'Visita de seguimiento programada',RESULTADO_REGISTRADO:'Resultado de la visita registrado',
    DOCUMENTO_SUSTENTO_ADJUNTADO:'Documento de sustento adjuntado',DILIGENCIA_INICIADA:'Diligencia iniciada',
    DILIGENCIA_REGISTRADA_POSTERIORMENTE:'Diligencia abierta para registro posterior',DILIGENCIA_NO_REALIZADA:'Visita registrada como no realizada',
    DILIGENCIA_FINALIZADA:'Diligencia finalizada',GPS_INICIO_REGISTRADO:'Ubicación de inicio registrada voluntariamente',
    PARTICIPANTE_AGREGADO:'Participante agregado',EVIDENCIA_AGREGADA:'Evidencia adjuntada',EVIDENCIA_RETIRADA:'Evidencia retirada',DOCUMENTO_ADJUNTADO:'Documento adjuntado',ACTA_ADJUNTA_SUBIDA:'Acta adjunta subida',ACTA_GENERADA:'Acta generada',
    ACTA_FIRMADA_SUBIDA:'Copia firmada adjuntada',COPIA_ENTREGADA:'Entrega de copia registrada',REVISION_INICIADA:'Revisión municipal iniciada',
    REVISION_REANUDADA:'Revisión municipal retomada',ACLARACION_SOLICITADA:'Aclaración solicitada',ACLARACION_RESPONDIDA:'Aclaración respondida',
    REVISION_FINALIZADA:'Revisión municipal finalizada',CONCLUSION_REGISTRADA:'Conclusión registrada',DERIVACION_RECOMENDADA:'Derivación recomendada',
    SEGUIMIENTO_REQUERIDO:'Seguimiento requerido',INFORME_CONCLUSION_GENERADO:'Informe de conclusión generado'
  } as Record<string,string>)[code]||code.toLowerCase().replaceAll('_',' ').replace(/^./,c=>c.toUpperCase());}
}

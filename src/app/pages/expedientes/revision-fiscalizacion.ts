import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AgendaVisita, ResumenDiario, VerificacionService } from '../../services/verificacion.service';
import { Fiscalizador } from '../../models/solicitud.model';
import { UsuariosService } from '../../services/usuarios.service';

@Component({selector:'app-revision-bandeja',standalone:true,imports:[CommonModule,FormsModule,RouterLink],templateUrl:'./revision-bandeja.html',styleUrls:['./revision.css','./revision-ui-fixes.css']})
export class RevisionBandejaComponent implements OnInit {
  items:any[]=[];resumen:ResumenDiario[]=[];resumenBase:ResumenDiario[]=[];agendaSeleccionada:AgendaVisita[]=[];agendaFecha='';cargando=true;error='';fecha='';fiscalizador='';origen='';resultado='';estado='';
  constructor(private api:VerificacionService,private router:Router,private cdr:ChangeDetectorRef){}
  async ngOnInit(){await this.cargar();}
  async cargar(){this.cargando=true;this.error='';try{[this.items,this.resumenBase]=await Promise.all([this.api.revisionesPendientes(),this.api.resumenDiario()]);await this.actualizarResumen();}catch{this.error='No se pudo cargar la bandeja de revisión.';}finally{this.cargando=false;this.cdr.markForCheck();}}
  async actualizarResumen(){this.resumen=await this.api.resumenDiario({fiscalizador:this.fiscalizador,origen:this.origen});if(this.agendaFecha&&!this.resumen.some(x=>x.fecha===this.agendaFecha)){this.agendaFecha='';this.agendaSeleccionada=[];}}
  async filtrosAgendaCambiaron(){this.error='';try{await this.actualizarResumen();if(this.agendaFecha)await this.cargarAgendaSeleccionada();}catch{this.error='No se pudo actualizar el resumen de agenda.';}this.cdr.markForCheck();}
  limpiarFiltros(){this.fecha='';this.fiscalizador='';this.origen='';this.resultado='';this.estado='';this.agendaFecha='';this.agendaSeleccionada=[];void this.filtrosAgendaCambiaron();}
  async cargarAgendaSeleccionada(){this.agendaSeleccionada=this.agendaFecha?await this.api.agendaProgramacion(this.agendaFecha).catch(()=>[]):[];}
  quitarFechaAgenda(){this.agendaFecha='';this.agendaSeleccionada=[];this.cdr.markForCheck();}
  async seleccionarFecha(fecha:string){this.agendaFecha=this.agendaFecha===fecha?'':fecha;await this.cargarAgendaSeleccionada();this.cdr.markForCheck();}
  get filtrados(){return this.items.filter(x=>(!this.fecha||String(x.fechaCierre||'').startsWith(this.fecha))&&(!this.fiscalizador||x.fiscalizador===this.fiscalizador)&&(!this.origen||x.origen===this.origen)&&(!this.resultado||x.resultado===this.resultado)&&(!this.estado||x.estadoRevision===this.estado));}
  get fiscalizadores(){return [...new Set([...this.resumenBase.flatMap(d=>Object.keys(d.porFiscalizador||{})),...this.items.map(x=>x.fiscalizador)])].filter(Boolean).sort();}
  get agendaSeleccionadaFiltrada(){return this.agendaSeleccionada.filter(x=>(!this.fiscalizador||x.fiscalizadorNombre===this.fiscalizador)&&(!this.origen||x.origen===this.origen)&&!['CANCELADA','REPROGRAMADA'].includes(x.estado));}
  fechaCorta(value:string){const [y,m,d]=String(value||'').slice(0,10).split('-');return y&&m&&d?`${d}/${m}/${y}`:'—';}
  fechaHoraLocal(value:string){const raw=String(value||'');const [date,time='']=raw.split('T');return `${this.fechaCorta(date)}${time?` ${time.slice(0,5)}`:''}`;}
  async revisar(item:any){try{const result=await this.api.iniciarRevision(item.diligenciaId);await this.router.navigate(['/revisiones',result.revision.id]);}catch{this.error='No se pudo iniciar la revisión. Puede que otro usuario ya la haya finalizado o el acta no esté disponible.';}this.cdr.markForCheck();}
  origenNombre(o:string){return ({SOLICITUD:'Solicitud',DENUNCIA:'Denuncia',INICIATIVA_MUNICIPAL:'Iniciativa municipal',ORDEN_SUPERIOR:'Orden superior',OPERATIVO_PROGRAMADO:'Operativo programado',OPERATIVO_INOPINADO:'Operativo inopinado',OTRO:'Otro'} as Record<string,string>)[o]||o;}
}

@Component({selector:'app-revision-detalle',standalone:true,imports:[CommonModule,FormsModule,RouterLink],templateUrl:'./revision-detalle.html',styleUrls:['./revision.css','./revision-ui-fixes.css']})
export class RevisionDetalleComponent implements OnInit {
  data:any=null;fiscalizadores:Fiscalizador[]=[];cargando=true;error='';mensaje='';conclusion='';fundamento='';observaciones='';descripcion='';plazo='';seguimiento=false;derivacion=false;otra='';documentos='';saving=false;
  private users=inject(UsuariosService);limiteDiario=5;sobrecargaConfirmada=false;agendaSeguimiento:AgendaVisita[]=[];followDate='';followTime='09:00';followType='PROGRAMADA';followFiscalizador='';followReason='';
  constructor(private route:ActivatedRoute,private api:VerificacionService,private cdr:ChangeDetectorRef){}
  async ngOnInit(){this.limiteDiario=await this.users.limiteDiario().catch(()=>5);await this.cargar();try{this.fiscalizadores=await this.api.obtenerFiscalizadores();}catch{}}
  async cargar(){this.cargando=true;try{this.data=await this.api.revision(Number(this.route.snapshot.paramMap.get('id')));}catch{this.error='No se pudo cargar el espacio de revisión.';}finally{this.cargando=false;this.cdr.markForCheck();}}
  get rev(){return this.data?.revision;}
  get finalizada(){return this.rev?.estado==='FINALIZADA';}
  get necesitaDetalle(){return ['RECOMENDACION_MEJORAS_CORRECCIONES','ADVERTENCIA','MEDIDA_CORRECTIVA','OTRA'].includes(this.conclusion);}
  get aclaracionPendiente(){return (this.data?.aclaraciones||[]).some((a:any)=>a.estado==='PENDIENTE');}
  get formularioValido(){
    if(!this.conclusion)return false;
    if(this.conclusion==='RECOMIENDA_INICIO_PROCEDIMIENTO'&&!this.derivacion)return false;
    return true;
  }
  cambiarConclusion(tipo:string){
    if(tipo===this.conclusion)return;
    this.conclusion=tipo;this.descripcion='';this.plazo='';this.otra='';this.documentos='';
    this.seguimiento=false;this.derivacion=false;this.error='';this.cdr.markForCheck();
  }
  cancelarSolicitud(){this.mensaje='';this.error='';this.cdr.markForCheck();}
  cancelarSeguimiento(){this.followDate='';this.followTime='09:00';this.followType='PROGRAMADA';this.followFiscalizador='';this.followReason='';this.error='';this.cdr.markForCheck();}
  async cargarAgendaSeguimiento(){this.sobrecargaConfirmada=false;this.agendaSeguimiento=this.followDate?await this.api.agendaProgramacion(this.followDate).catch(()=>[]):[];this.cdr.markForCheck();}
  get cargaSeguimiento(){return this.agendaSeguimiento.filter(v=>!['CANCELADA','REPROGRAMADA'].includes(v.estado)).length;}
  async solicitar(){if(!this.mensaje.trim())return;this.saving=true;try{await this.api.solicitarAclaracion(this.rev.id,this.mensaje);this.mensaje='';await this.cargar();}catch{this.error='No se pudo solicitar la aclaración.';}finally{this.saving=false;this.cdr.markForCheck();}}
  async concluir(){if(!this.formularioValido){this.error='Selecciona una conclusión y confirma la derivación cuando corresponda.';return;}this.saving=true;try{await this.api.concluirRevision(this.rev.id,{conclusion:this.conclusion,fundamentoConclusion:this.fundamento,observacionesRevision:this.observaciones,descripcionConclusion:this.descripcion,plazoConclusion:this.plazo,requiereSeguimiento:this.seguimiento,requiereDerivacion:this.derivacion,denominacionOtra:this.otra,referenciaDocumentos:this.documentos});await this.cargar();}catch{this.error='No se pudo registrar la conclusión. Verifica aclaraciones pendientes y el estado del expediente.';}finally{this.saving=false;this.cdr.markForCheck();}}
  async programarSeguimiento(){try{const agenda=await this.api.agendaProgramacion(this.followDate);const count=agenda.filter(v=>!['CANCELADA','REPROGRAMADA'].includes(v.estado)).length+1;if(count>this.limiteDiario&&!this.sobrecargaConfirmada){this.sobrecargaConfirmada=true;this.error='Esta visita superará el límite orientativo de '+this.limiteDiario+'. Pulsa Programar otra vez para continuar.';return;}}catch{this.error='No se pudo consultar la carga de la fecha.';return;}this.saving=true;try{await this.api.crearSeguimiento(this.rev.id,{fecha:this.followDate,hora:this.followTime,tipoVisita:this.followType,fiscalizadorId:this.followFiscalizador,motivo:this.followReason});this.followReason='';await this.cargar();}catch{this.error='No se pudo crear la programación de seguimiento.';}finally{this.saving=false;this.cdr.markForCheck();}}
  async abrirEvidencia(e:any){try{const b=await this.api.evidenciaRevision(this.data.diligenciaId,e.id);const url=URL.createObjectURL(b);window.open(url,'_blank','noopener');setTimeout(()=>URL.revokeObjectURL(url),60000);}catch{this.error='No se pudo abrir la evidencia.';}}
  async descargarActa(){try{const b=await this.api.descargarActa(this.data.acta.id);this.abrirBlob(b,`${this.data.acta.codigo}.pdf`);}catch{this.error='No se pudo descargar el PDF del acta.';}}
  async descargarFirmada(){try{const b=await this.api.descargarCopiaFirmadaActa(this.data.acta.id);this.abrirBlob(b,this.data.acta.pdfFirmado||'acta-firmada.pdf');}catch{this.error='No hay copia firmada disponible.';}}
  async informe(){try{const b=await this.api.descargarInformeConclusion(this.rev.id);this.abrirBlob(b,this.rev.informe||'informe-conclusion.pdf');}catch{this.error='No se pudo descargar el informe.';}}
  private abrirBlob(blob:Blob,name:string){const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),60000);}
}

import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ActaFiscalizacion, Expediente, ProgramacionExpediente, VerificacionService } from '../../services/verificacion.service';
import { Fiscalizador } from '../../models/solicitud.model';

@Component({selector:'app-expediente-detalle',standalone:true,imports:[CommonModule,FormsModule,RouterLink],templateUrl:'./expediente-detalle.html',styleUrls:['./expedientes.css','./expediente-detalle.css']})
export class ExpedienteDetalleComponent implements OnInit {
  expediente:Expediente|null=null; actas:ActaFiscalizacion[]=[]; ciclos:any[]=[]; fiscalizadores:Fiscalizador[]=[]; cargando=true; error=''; accion:''|'reprogramar'|'cancelar'=''; programacion:ProgramacionExpediente|null=null;
  fecha='';hora='09:00';fiscalizadorId='';tipoVisita='PROGRAMADA';motivo='';
  constructor(private route:ActivatedRoute,private router:Router,private api:VerificacionService,private cdr:ChangeDetectorRef){}
  async ngOnInit(){await this.cargar();try{this.fiscalizadores=await this.api.obtenerFiscalizadores();}catch{}}
  async cargar(){this.cargando=true;try{this.expediente=await this.api.obtenerExpediente(this.route.snapshot.paramMap.get('codigo')||'');const [actas,ciclos]=await Promise.all([this.api.actasExpediente(this.expediente.codigo),this.api.ciclosFiscalizacion(this.expediente.codigo)]);this.actas=actas;this.ciclos=ciclos.ciclos||[];}catch{this.error='No se pudo cargar el expediente, actas o ciclo de fiscalización.';}finally{this.cargando=false;this.cdr.markForCheck();}}
  origen(o:string){return ({SOLICITUD:'Solicitud',DENUNCIA:'Denuncia',INICIATIVA_MUNICIPAL:'Iniciativa municipal',ORDEN_SUPERIOR:'Orden superior',OPERATIVO_PROGRAMADO:'Operativo programado',OPERATIVO_INOPINADO:'Operativo inopinado',OTRO:'Otro'} as Record<string,string>)[o]||o;}
  vigente(){return this.expediente?[...this.expediente.programaciones].reverse().find(p=>p.estado!=='REPROGRAMADA')??null:null;}
  abrir(a:'reprogramar'|'cancelar',p:ProgramacionExpediente){this.accion=a;this.programacion=p;this.fecha='';this.hora=p.hora||'09:00';this.fiscalizadorId=p.fiscalizadorId||'';this.tipoVisita=p.tipoVisita;this.motivo='';this.error='';}
  cerrar(){this.accion='';this.programacion=null;}
  puedeModificar(p:ProgramacionExpediente){return this.expediente?.estado!=='CERRADO'&&['PROGRAMADA','VENCIDA'].includes(p.estado);}
  async revisar(d:any){try{const r=await this.api.iniciarRevision(d.diligenciaId);await this.router.navigate(['/revisiones',r.revision.id]);}catch{this.error='No se pudo abrir la revisión de esta diligencia.';}this.cdr.markForCheck();}
  async guardar(){if(!this.programacion||!this.motivo.trim()){this.error='El motivo es obligatorio.';return;}try{if(this.accion==='reprogramar'){await this.api.reprogramar(this.programacion.id,{fecha:this.fecha,hora:this.hora,motivo:this.motivo,fiscalizadorId:this.fiscalizadorId||null,tipoVisita:this.tipoVisita});}else if(this.accion==='cancelar'){await this.api.cancelarProgramacion(this.programacion.id,this.motivo);}this.cerrar();await this.cargar();}catch{this.error='No se pudo completar la operación. Verifica fecha, disponibilidad y permisos.';}this.cdr.markForCheck();}
  async documento(){if(!this.expediente)return;try{const blob=await this.api.abrirSustento(this.expediente.codigo);window.open(URL.createObjectURL(blob),'_blank','noopener');}catch{this.error='No se pudo abrir el documento de sustento.';}}
  async reporte(){if(!this.expediente?.solicitudCodigo)return;try{const blob=await this.api.abrirReporte(this.expediente.solicitudCodigo);window.open(URL.createObjectURL(blob),'_blank','noopener');}catch{this.error='No se pudo abrir el reporte del fiscalizador.';}}
  async descargarActa(acta:ActaFiscalizacion){try{const blob=await this.api.descargarActa(acta.id);this.abrirBlob(blob,acta.pdfGeneradoNombre);}catch{this.error='No se pudo descargar el acta generada.';}}
  async descargarCopiaFirmada(acta:ActaFiscalizacion){try{const blob=await this.api.descargarCopiaFirmadaActa(acta.id);this.abrirBlob(blob,acta.pdfFirmadoNombre||'Acta-firmada.pdf');}catch{this.error='No se pudo descargar la copia firmada.';}}
  private abrirBlob(blob:Blob,nombre:string|null){const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=nombre||'Acta.pdf';a.click();setTimeout(()=>URL.revokeObjectURL(url),60000);}
  parse(v:string|null){if(!v)return '';try{return JSON.stringify(JSON.parse(v),null,2);}catch{return v;}}
}

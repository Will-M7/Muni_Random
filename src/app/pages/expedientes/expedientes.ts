import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Expediente, VerificacionService } from '../../services/verificacion.service';
import { AuthService } from '../../services/auth.service';

@Component({selector:'app-expedientes',imports:[CommonModule,FormsModule,RouterLink],templateUrl:'./expedientes.html',styleUrl:'./expedientes.css'})
export class ExpedientesComponent implements OnInit {
  expedientes:Expediente[]=[]; cargando=true; error=''; q=''; origen=''; estado=''; estadoVisita=''; fiscalizador=''; fecha='';
  constructor(private service:VerificacionService,private auth:AuthService,private cdr:ChangeDetectorRef){}
  get puedeRevisar(){return this.auth.hasCapability('REVISION_VER');}
  async ngOnInit(){await this.cargar();}
  async cargar(){this.cargando=true;this.error='';try{this.expedientes=await this.service.listarExpedientes();}catch{this.error='No se pudieron cargar los expedientes.';}finally{this.cargando=false;this.cdr.markForCheck();}}
  get filtrados(){const q=this.q.trim().toLocaleLowerCase();return this.expedientes.filter(e=>{const p=this.vigente(e);const txt=[e.codigo,e.origen,e.detalleOrigen,e.observacionesIniciales,e.solicitudCodigo,e.nombreAdministrado,e.administradoApellidos,e.documentoAdministrado,e.direccionFiscalizada,e.referenciaDomicilio,e.objetoFiscalizacion,p?.fiscalizadorNombre].join(' ').toLocaleLowerCase();return(!q||txt.includes(q))&&(!this.origen||e.origen===this.origen)&&(!this.estado||e.estado===this.estado)&&(!this.estadoVisita||p?.estado===this.estadoVisita)&&(!this.fiscalizador||p?.fiscalizadorId===this.fiscalizador)&&(!this.fecha||e.fechaCreacion?.startsWith(this.fecha));});}
  vigente(e:Expediente){return [...(e.programaciones||[])].reverse().find(p=>p.estado!=='REPROGRAMADA')??null;}
  administrado(e:Expediente){return [e.nombreAdministrado,e.administradoApellidos].filter(Boolean).join(' ')||'Administrado sin identificar';}
  opcionesFiscalizador(){return [...new Map(this.expedientes.flatMap(e=>e.programaciones).filter(p=>p.fiscalizadorId).map(p=>[p.fiscalizadorId!,p.fiscalizadorNombre||p.fiscalizadorId!])).entries()];}
  origenNombre(o:string){return ({SOLICITUD:'Solicitud',DENUNCIA:'Denuncia',INICIATIVA_MUNICIPAL:'Iniciativa municipal',ORDEN_SUPERIOR:'Orden superior',OPERATIVO_PROGRAMADO:'Operativo programado',OPERATIVO_INOPINADO:'Operativo inopinado',OTRO:'Otro'} as Record<string,string>)[o]||o;}
}

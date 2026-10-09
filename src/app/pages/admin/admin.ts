import {ChangeDetectorRef,Component,OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {HttpErrorResponse} from '@angular/common/http';
import {IdentityConfiguration,UserPreferences,Usuario,UsuarioNuevo,UsuariosService} from '../../services/usuarios.service';

@Component({selector:'app-admin',imports:[CommonModule,FormsModule],templateUrl:'./admin.html',styleUrl:'./admin.css'})
export class AdminComponent implements OnInit {
  seccion:'usuarios'|'ajustes'|'integraciones'='usuarios'; usuarios:Usuario[]=[];selected:Usuario|null=null;
  nuevo:UsuarioNuevo={nombre:'',username:'',password:'',rol:'MUNICIPIO'};
  preferencias:UserPreferences={mapaInterno:true,googleExterno:true,googleUrl:true,descargarJornadaHtml:true};
  integracion:IdentityConfiguration={provider:'DISABLED',endpoint:null,credentialConfigured:false,actualizadoPor:null,actualizadoEn:null};
  apiKey='';clientId='';clientSecret='';limite=5;cargando=false;guardando=false;mensaje='';error='';
  constructor(private service:UsuariosService,private cdr:ChangeDetectorRef){}
  async ngOnInit(){await Promise.all([this.cargarUsuarios(),this.cargarParametros()]);}
  async cargarUsuarios(){this.cargando=true;try{this.usuarios=await this.service.listar();}catch{this.error='No se pudo cargar la lista de usuarios.';}finally{this.cargando=false;this.cdr.markForCheck();}}
  async cargarParametros(){try{[this.integracion,this.limite]=await Promise.all([this.service.integracion(),this.service.limiteDiario()]);}catch{this.error='No se pudo cargar la configuración.';}this.cdr.markForCheck();}
  async crearUsuario(){this.error='';this.mensaje='';if(this.nuevo.password.length<8){this.error='La contraseña debe tener al menos 8 caracteres.';return;}this.guardando=true;try{await this.service.crear(this.nuevo);this.nuevo={nombre:'',username:'',password:'',rol:'MUNICIPIO'};this.mensaje='Usuario creado correctamente.';await this.cargarUsuarios();}catch(e){this.error=this.errorMessage(e,'No se pudo crear el usuario.');}finally{this.guardando=false;this.cdr.markForCheck();}}
  async cambiarEstado(user:Usuario){this.error='';try{Object.assign(user,await this.service.cambiarEstado(user.username,!user.activo));}catch(e){this.error=this.errorMessage(e,'No se pudo cambiar el estado.');}this.cdr.markForCheck();}
  async abrirAjustes(user:Usuario){this.selected=user;this.seccion='ajustes';this.error='';try{this.preferencias=await this.service.preferencias(user.username);}catch{this.error='No se pudieron cargar los ajustes.';}this.cdr.markForCheck();}
  async guardarAjustes(){if(!this.selected)return;this.error='';try{this.preferencias=await this.service.guardarPreferencias(this.selected.username,this.preferencias);this.mensaje=`Ajustes guardados para ${this.selected.nombre}.`;}catch(e){this.error=this.errorMessage(e,'No se pudieron guardar los ajustes.');}this.cdr.markForCheck();}
  async guardarIntegracion(){this.error='';try{this.integracion=await this.service.guardarIntegracion({provider:this.integracion.provider,endpoint:this.integracion.endpoint,apiKey:this.apiKey||null,clientId:this.clientId||null,clientSecret:this.clientSecret||null});this.apiKey='';this.clientId='';this.clientSecret='';this.mensaje='Integración DNI guardada.';}catch(e){this.error=this.errorMessage(e,'No se pudo guardar la integración.');}this.cdr.markForCheck();}
  async probarIntegracion(){try{this.mensaje=(await this.service.probarIntegracion()).message;}catch(e){this.error=this.errorMessage(e,'No se pudo verificar la configuración.');}this.cdr.markForCheck();}
  async guardarLimite(){this.error='';try{this.limite=await this.service.guardarLimite(this.limite);this.mensaje='Límite orientativo guardado.';}catch(e){this.error=this.errorMessage(e,'No se pudo guardar el límite.');}this.cdr.markForCheck();}
  private errorMessage(e:unknown,fallback:string){return e instanceof HttpErrorResponse?e.error?.message||fallback:fallback;}
}

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { Rol } from '../models/solicitud.model';
export interface Sesion { token:string; nombre:string; cargo:string; correo:string; rol:Rol; roles?:Rol[]; capacidades?:string[]; fiscalizadorId?:string; }
@Injectable({providedIn:'root'}) export class AuthService { private key='sm_session'; constructor(private http:HttpClient){} get session():Sesion|null{const raw=localStorage.getItem(this.key);try{return raw?JSON.parse(raw):null;}catch{return null;}} get token():string|null{return this.session?.token??null;} hasCapability(capability:string):boolean{return this.session?.capacidades?.includes(capability)??false;} homeRoute(session:Sesion|null=this.session):string {
    const caps=session?.capacidades??[];const roles=session?.roles??(session?.rol?[session.rol]:[]);
    if(roles.includes('ADMIN_SISTEMA'))return '/admin';
    if(roles.includes('MUNICIPIO')&&caps.includes('EXPEDIENTE_VER'))return '/expedientes';
    if(roles.includes('FISCALIZADOR')&&caps.includes('TAREAS_PROPIAS_VER'))return '/fiscalizador';
    if(caps.includes('EXPEDIENTE_VER'))return '/expedientes';
    if(caps.includes('TAREAS_PROPIAS_VER'))return '/fiscalizador';
    if(caps.includes('SOLICITUD_VER'))return '/solicitudes';
    return '/perfil';
  } isAuthenticated():boolean{const token=this.token;if(!token)return false;try{const payload=JSON.parse(atob(token.split('.')[1].replace(/-/g,'+').replace(/_/g,'/')));return typeof payload.exp!=='number'||payload.exp*1000>Date.now();}catch{return false;}} async login(usuario:string,password:string):Promise<Sesion>{const s=await firstValueFrom(this.http.post<Sesion>(`${environment.apiUrl}/api/auth/login`,{username:usuario,password}));localStorage.setItem(this.key,JSON.stringify(s));return s;} logout():void{localStorage.removeItem(this.key);} }

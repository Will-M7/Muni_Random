import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { Rol } from '../models/solicitud.model';
export interface Sesion { token:string; nombre:string; cargo:string; correo:string; rol:Rol; fiscalizadorId?:string; }
@Injectable({providedIn:'root'}) export class AuthService { private key='sm_session'; constructor(private http:HttpClient){} get session():Sesion|null{const raw=localStorage.getItem(this.key);try{return raw?JSON.parse(raw):null;}catch{return null;}} get token():string|null{return this.session?.token??null;} isAuthenticated():boolean{const token=this.token;if(!token)return false;try{const payload=JSON.parse(atob(token.split('.')[1].replace(/-/g,'+').replace(/_/g,'/')));return typeof payload.exp!=='number'||payload.exp*1000>Date.now();}catch{return false;}} async login(usuario:string,password:string):Promise<Sesion>{const s=await firstValueFrom(this.http.post<Sesion>(`${environment.apiUrl}/api/auth/login`,{username:usuario,password}));localStorage.setItem(this.key,JSON.stringify(s));localStorage.setItem('rolSM',s.rol);localStorage.setItem('usuario_autenticado',JSON.stringify(s));return s;} logout():void{localStorage.removeItem(this.key);localStorage.removeItem('rolSM');localStorage.removeItem('usuario_autenticado');} }

import {Component} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {provideRouter,Router} from '@angular/router';
import {RouterTestingHarness} from '@angular/router/testing';
import {describe,it,expect} from 'vitest';
import {AuthService,Sesion} from '../services/auth.service';
import {homeGuard,capabilityGuard} from './auth.guard';
@Component({template:'<p>Módulo autorizado</p>'}) class Module{}
describe('rutas de entrada sin pantalla de inicio',()=>{
  for(const [rol,caps,target] of [['ADMIN_SISTEMA',['USUARIOS_VER'],'/admin'],['MUNICIPIO',['EXPEDIENTE_VER'],'/expedientes'],['FISCALIZADOR',['TAREAS_PROPIAS_VER'],'/fiscalizador']] as const){it(`redirige /inicio y / al módulo de ${rol}`,async()=>{const session={rol,roles:[rol],capacidades:[...caps]} as Sesion;const auth={isAuthenticated:()=>true,homeRoute:()=>AuthService.prototype.homeRoute.call({} as AuthService,session)};TestBed.configureTestingModule({providers:[{provide:AuthService,useValue:auth},provideRouter([{path:'',canActivate:[homeGuard],children:[],pathMatch:'full'},{path:'inicio',canActivate:[homeGuard],children:[]},{path:target.slice(1),component:Module}])]});const harness=await RouterTestingHarness.create();await harness.navigateByUrl('/inicio');expect(TestBed.inject(Router).url).toBe(target);await harness.navigateByUrl('/');expect(TestBed.inject(Router).url).toBe(target);});}
  it('envía una sesión vencida a login',async()=>{TestBed.configureTestingModule({providers:[{provide:AuthService,useValue:{isAuthenticated:()=>false}},provideRouter([{path:'inicio',canActivate:[homeGuard],children:[]},{path:'login',component:Module}])]});await (await RouterTestingHarness.create()).navigateByUrl('/inicio');expect(TestBed.inject(Router).url).toBe('/login');});
  it('redirecciona rutas sin capacidad al módulo autorizado',async()=>{const auth={isAuthenticated:()=>true,hasCapability:()=>false,homeRoute:()=>'/fiscalizador'};TestBed.configureTestingModule({providers:[{provide:AuthService,useValue:auth},provideRouter([{path:'expedientes',component:Module,canActivate:[capabilityGuard],data:{capability:'EXPEDIENTE_VER'}},{path:'fiscalizador',component:Module}])]});await (await RouterTestingHarness.create()).navigateByUrl('/expedientes');expect(TestBed.inject(Router).url).toBe('/fiscalizador');});
});

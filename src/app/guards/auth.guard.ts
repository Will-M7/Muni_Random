import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
export const authGuard:CanActivateFn=()=>inject(AuthService).isAuthenticated()?true:inject(Router).createUrlTree(['/login']);
export const homeGuard:CanActivateFn=()=>{const auth=inject(AuthService);return inject(Router).createUrlTree([auth.isAuthenticated()?auth.homeRoute():'/login']);};
export const capabilityGuard:CanActivateFn=(route)=>{const auth=inject(AuthService);const router=inject(Router);if(!auth.isAuthenticated())return router.createUrlTree(['/login']);const capability=route.data['capability'] as string|undefined;return !capability||auth.hasCapability(capability)?true:router.createUrlTree([auth.homeRoute()]);};
export const adminGuard:CanActivateFn=()=>{const auth=inject(AuthService);return auth.isAuthenticated()&&(auth.session?.roles??[auth.session?.rol]).includes('ADMIN_SISTEMA')?true:inject(Router).createUrlTree([auth.isAuthenticated()?auth.homeRoute():'/login']);};

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface DatosDniReniec {
  dni: string;
  nombres: string;
  apellidoPaterno: string;
  apellidoMaterno: string;
  sexo?: string;
  fechaNacimiento?: string;
}

@Injectable({ providedIn: 'root' })
export class ReniecService {
  constructor(private http: HttpClient) {}

  consultarDni(dni: string): Observable<DatosDniReniec> {
    return this.http.get<DatosDniReniec>(`${environment.apiUrl}/api/reniec/${dni}`);
  }
}

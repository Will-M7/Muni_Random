import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-predial',
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './predial.html',
  styleUrl: './predial.css'
})
export class PredialComponent{
  direccion = ''; referencia = ''; tipoVivienda = ''; codigoPredial = '';
  latitud: number | null = null; longitud: number | null = null; mensaje = '';
  guardando = false;
  constructor(private http: HttpClient) {}

  registrarVivienda(): void {
    this.mensaje = '';
    if (!this.direccion.trim() || !this.tipoVivienda || !this.codigoPredial.trim()) { this.mensaje = 'Completa dirección, tipo de vivienda y código predial.'; return; }
    if (this.guardando) return;
    this.guardando = true;
    this.http.post(`${environment.apiUrl}/api/predios`, { direccion: this.direccion.trim(), referencia: this.referencia.trim(), tipoVivienda: this.tipoVivienda, codigoPredial: this.codigoPredial.trim(), latitud: this.latitud, longitud: this.longitud }).subscribe({ next: () => { this.mensaje = '¡Vivienda registrada correctamente!'; this.guardando = false; }, error: () => { this.mensaje = 'No se pudo registrar la vivienda.'; this.guardando = false; } });
  }

}
export { PredialComponent as Predial };

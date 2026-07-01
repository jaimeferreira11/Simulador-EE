import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RespuestaAsistente } from '../models/asistente.model';

@Injectable({ providedIn: 'root' })
export class AsistenteApiService {
  private http = inject(HttpClient);

  preguntar(codigo: string, pregunta: string): Observable<RespuestaAsistente> {
    return this.http.post<RespuestaAsistente>(
      `/v1/competencias/${codigo}/asistente`,
      { pregunta },
    );
  }
}

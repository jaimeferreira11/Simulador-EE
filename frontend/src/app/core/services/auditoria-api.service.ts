import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PagedAuditoriaEventos } from '../models/auditoria.model';

@Injectable({ providedIn: 'root' })
export class AuditoriaApiService {
  private http = inject(HttpClient);

  list(competenciaId: number, page = 0, size = 50): Observable<PagedAuditoriaEventos> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PagedAuditoriaEventos>(
      `/v1/competencias/${competenciaId}/auditoria`,
      { params }
    );
  }
}

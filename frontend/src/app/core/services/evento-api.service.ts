import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { EventoAutomatico, EventoCompetencia, EventoDisparar } from '../models/evento.model';

@Injectable({ providedIn: 'root' })
export class EventoApiService {
  private http = inject(HttpClient);

  listByCompetencia(competenciaId: number, trimestreId?: number): Observable<EventoCompetencia[]> {
    const params: Record<string, string> = {};
    if (trimestreId) params['trimestre_id'] = String(trimestreId);
    return this.http.get<EventoCompetencia[]>(`/v1/competencias/${competenciaId}/eventos`, { params });
  }

  disparar(competenciaId: number, data: EventoDisparar): Observable<EventoCompetencia> {
    return this.http.post<EventoCompetencia>(`/v1/competencias/${competenciaId}/eventos`, data);
  }

  listAutomaticos(competenciaId: number): Observable<EventoAutomatico[]> {
    return this.http.get<EventoAutomatico[]>(`/v1/competencias/${competenciaId}/eventos-automaticos`);
  }
}

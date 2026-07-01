import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Trimestre } from '../models/trimestre.model';

@Injectable({ providedIn: 'root' })
export class TrimestreApiService {
  private http = inject(HttpClient);

  listByCompetencia(competenciaId: number): Observable<Trimestre[]> {
    return this.http.get<Trimestre[]>(`/v1/competencias/${competenciaId}/trimestres`);
  }

  getById(id: number): Observable<Trimestre> {
    return this.http.get<Trimestre>(`/v1/trimestres/${id}`);
  }

  abrir(id: number): Observable<Trimestre> {
    return this.http.post<Trimestre>(`/v1/trimestres/${id}/abrir`, {});
  }

  cerrar(id: number): Observable<Trimestre> {
    return this.http.post<Trimestre>(`/v1/trimestres/${id}/cerrar`, {});
  }
}

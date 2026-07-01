import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CoachingTrimestre, RankingItem, ResultadoCalculo, SnapshotEstado, EvolucionEquipo } from '../models/resultado.model';

@Injectable({ providedIn: 'root' })
export class ResultadoApiService {
  private http = inject(HttpClient);

  getRanking(competenciaId: number, trimestreId?: number): Observable<RankingItem[]> {
    let params = new HttpParams();
    if (trimestreId) params = params.set('trimestre_id', trimestreId);
    return this.http.get<RankingItem[]>(`/v1/competencias/${competenciaId}/ranking`, { params });
  }

  getEvolucion(competenciaId: number): Observable<EvolucionEquipo[]> {
    return this.http.get<EvolucionEquipo[]>(`/v1/competencias/${competenciaId}/ranking/evolucion`);
  }

  getSnapshot(equipoId: number, trimestreId: number, momento: 'INICIO' | 'CIERRE'): Observable<SnapshotEstado> {
    return this.http.get<SnapshotEstado>(
      `/v1/equipos/${equipoId}/trimestres/${trimestreId}/snapshot`,
      { params: { momento } }
    );
  }

  getResultado(equipoId: number, trimestreId: number): Observable<ResultadoCalculo> {
    return this.http.get<ResultadoCalculo>(`/v1/equipos/${equipoId}/trimestres/${trimestreId}/resultado`);
  }

  getCoaching(equipoId: number, trimestreId: number): Observable<CoachingTrimestre> {
    return this.http.get<CoachingTrimestre>(
      `/v1/equipos/${equipoId}/trimestres/${trimestreId}/coaching`
    );
  }

  exportExcel(competenciaId: number): Observable<Blob> {
    return this.http.get(`/v1/competencias/${competenciaId}/export/excel`, { responseType: 'blob' });
  }

  exportPdf(competenciaId: number, sections?: string[]): Observable<Blob> {
    let params = new HttpParams();
    if (sections?.length) {
      for (const s of sections) {
        params = params.append('sections', s);
      }
    }
    return this.http.get(`/v1/competencias/${competenciaId}/export/pdf`, { params, responseType: 'blob' });
  }

  exportCsv(competenciaId: number, sections?: string[]): Observable<Blob> {
    let params = new HttpParams();
    if (sections?.length) {
      for (const s of sections) {
        params = params.append('sections', s);
      }
    }
    return this.http.get(`/v1/competencias/${competenciaId}/export/csv`, { params, responseType: 'blob' });
  }
}

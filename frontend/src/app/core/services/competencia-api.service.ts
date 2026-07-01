import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Competencia,
  CompetenciaCreate,
  CompetenciaDetalle,
  CompetenciaUpdate,
  Entidad,
  EscenarioPredefinido,
  PagedCompetencias,
  EstadoCompetencia,
} from '../models/competencia.model';

@Injectable({ providedIn: 'root' })
export class CompetenciaApiService {
  private http = inject(HttpClient);

  list(
    page = 0,
    size = 20,
    estado?: EstadoCompetencia,
    entidadId?: number,
    anio?: number,
  ): Observable<PagedCompetencias> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (estado) params = params.set('estado', estado);
    if (entidadId) params = params.set('entidad_id', entidadId);
    if (anio) params = params.set('anio', anio);
    return this.http.get<PagedCompetencias>('/v1/competencias', { params });
  }

  listEntidades(): Observable<Entidad[]> {
    return this.http.get<Entidad[]>('/v1/entidades');
  }

  getEscenarios(rubroId?: number): Observable<EscenarioPredefinido[]> {
    let params = new HttpParams();
    if (rubroId) params = params.set('rubro_id', rubroId);
    return this.http.get<EscenarioPredefinido[]>('/v1/escenarios', { params });
  }

  getById(id: number): Observable<CompetenciaDetalle> {
    return this.http.get<CompetenciaDetalle>(`/v1/competencias/${id}`);
  }

  create(data: CompetenciaCreate): Observable<Competencia> {
    return this.http.post<Competencia>('/v1/competencias', data);
  }

  update(id: number, data: CompetenciaUpdate): Observable<Competencia> {
    return this.http.patch<Competencia>(`/v1/competencias/${id}`, data);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/v1/competencias/${id}`);
  }

  abrirInscripcion(id: number): Observable<Competencia> {
    return this.http.post<Competencia>(`/v1/competencias/${id}/abrir-inscripcion`, {});
  }

  iniciar(id: number): Observable<Competencia> {
    return this.http.post<Competencia>(`/v1/competencias/${id}/iniciar`, {});
  }

  pausar(id: number): Observable<void> {
    return this.http.post<void>(`/v1/competencias/${id}/pausar`, {});
  }

  reanudar(id: number): Observable<void> {
    return this.http.post<void>(`/v1/competencias/${id}/reanudar`, {});
  }

  finalizar(id: number): Observable<void> {
    return this.http.post<void>(`/v1/competencias/${id}/finalizar`, {});
  }
}

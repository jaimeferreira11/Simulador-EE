import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Equipo, EquipoCreate, EquipoDetalle, EquipoMiembro } from '../models/equipo.model';

@Injectable({ providedIn: 'root' })
export class EquipoApiService {
  private http = inject(HttpClient);

  listByCompetencia(competenciaId: number): Observable<Equipo[]> {
    return this.http.get<Equipo[]>(`/v1/competencias/${competenciaId}/equipos`);
  }

  getById(id: number): Observable<EquipoDetalle> {
    return this.http.get<EquipoDetalle>(`/v1/equipos/${id}`);
  }

  create(competenciaId: number, data: EquipoCreate): Observable<Equipo> {
    return this.http.post<Equipo>(`/v1/competencias/${competenciaId}/equipos`, data);
  }

  addMiembro(
    equipoId: number,
    data: { usuario_id: number; es_capitan?: boolean; area_id?: number | null },
  ): Observable<EquipoMiembro> {
    return this.http.post<EquipoMiembro>(`/v1/equipos/${equipoId}/miembros`, data);
  }

  removeMiembro(equipoId: number, miembroId: number): Observable<void> {
    return this.http.delete<void>(`/v1/equipos/${equipoId}/miembros/${miembroId}`);
  }

  setCapitan(equipoId: number, miembroId: number): Observable<void> {
    return this.http.put<void>(`/v1/equipos/${equipoId}/miembros/${miembroId}/capitan`, {});
  }

  updateMiembroArea(
    equipoId: number,
    miembroId: number,
    areaId: number | null,
  ): Observable<EquipoMiembro> {
    return this.http.put<EquipoMiembro>(
      `/v1/equipos/${equipoId}/miembros/${miembroId}/area`,
      { area_id: areaId },
    );
  }

  updateEstado(equipoId: number, estado: string, justificacion: string): Observable<Equipo> {
    return this.http.patch<Equipo>(`/v1/equipos/${equipoId}`, { estado, justificacion });
  }
}

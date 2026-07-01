import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Invitacion {
  id: number;
  equipoId: number;
  email: string;
  nombreCompleto: string;
  estado: string;
  createdAt: string | null;
  expiresAt: string | null;
}

export interface InvitarRequest {
  email: string;
  nombreCompleto: string;
  areaId?: number | null;
  esCapitan?: boolean;
}

export interface InvitacionDetalle {
  id: number;
  email: string;
  nombreCompleto: string;
  estado: string;
  equipoNombre: string;
  equipoColor: string;
  competenciaNombre: string;
  competenciaCodigo: string;
  expiresAt: string | null;
}

export interface ImportInvitadoEquipo {
  fila: number;
  email: string;
}

export interface ImportErrorEquipo {
  fila: number;
  email: string;
  motivo: string;
}

export interface ImportResultEquipo {
  total: number;
  invitados: ImportInvitadoEquipo[];
  errores: ImportErrorEquipo[];
}

@Injectable({ providedIn: 'root' })
export class InvitacionApiService {
  private http = inject(HttpClient);

  listByEquipo(equipoId: number): Observable<Invitacion[]> {
    return this.http.get<Invitacion[]>(`/v1/equipos/${equipoId}/invitaciones`);
  }

  invitar(equipoId: number, data: InvitarRequest): Observable<Invitacion> {
    return this.http.post<Invitacion>(`/v1/equipos/${equipoId}/invitaciones`, data);
  }

  cancelar(invitacionId: number): Observable<void> {
    return this.http.delete<void>(`/v1/invitaciones/${invitacionId}`);
  }

  reenviar(equipoId: number, data: InvitarRequest): Observable<Invitacion> {
    return this.http.post<Invitacion>(`/v1/equipos/${equipoId}/invitaciones`, data);
  }

  importMiembrosCsv(equipoId: number, file: File): Observable<ImportResultEquipo> {
    const formData = new FormData();
    formData.append('file', file);
    // No Content-Type header: the browser sets the multipart boundary automatically.
    return this.http.post<ImportResultEquipo>(
      `/v1/equipos/${equipoId}/invitaciones/import`,
      formData,
    );
  }

  getByToken(token: string): Observable<InvitacionDetalle> {
    return this.http.get<InvitacionDetalle>(`/v1/invitaciones/token/${token}`);
  }

  aceptar(token: string, password: string): Observable<{ mensaje: string }> {
    return this.http.post<{ mensaje: string }>(`/v1/invitaciones/token/${token}/aceptar`, {
      password,
    });
  }
}

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Usuario } from '../models/usuario.model';

interface PagedUsuarios {
  content: Usuario[];
  totalElements: number;
  page: number;
  size: number;
}

/**
 * Payload para crear un usuario via POST /v1/usuarios (snake_case segun el contrato OpenAPI).
 * El backend envia al usuario un email para que defina su propia contrasena, por lo que
 * `password` es opcional y normalmente no se envia desde el flujo del moderador.
 */
export interface UsuarioCreate {
  email: string;
  nombre_completo: string;
  rol_codigo: string;
  password?: string;
}

/**
 * Resultado de la importacion masiva via POST /v1/usuarios/import (snake_case).
 * El backend procesa el CSV de inmediato al subirlo (no hay paso de confirmacion).
 */
export interface ImportResult {
  total: number;
  creados: { fila: number; email: string; usuario_id: number }[];
  errores: { fila: number; email: string; motivo: string }[];
}

/**
 * Servicio para el endpoint compartido /v1/usuarios (accesible por MODERADOR y ADMIN_PLATAFORMA).
 * NO usar /v1/admin/usuarios aqui: ese namespace es exclusivo de ADMIN_PLATAFORMA.
 */
@Injectable({ providedIn: 'root' })
export class UsuarioApiService {
  private http = inject(HttpClient);
  private base = '/v1/usuarios';

  search(query: string): Observable<PagedUsuarios> {
    return this.http.get<PagedUsuarios>(this.base, {
      params: { search: query, rol: 'JUGADOR' },
    });
  }

  list(
    page = 0,
    size = 20,
    rol?: string,
    activo?: boolean,
    search?: string,
  ): Observable<PagedUsuarios> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (rol) params = params.set('rol', rol);
    if (activo !== undefined) params = params.set('activo', activo);
    if (search) params = params.set('search', search);
    return this.http.get<PagedUsuarios>(this.base, { params });
  }

  create(req: UsuarioCreate): Observable<Usuario> {
    return this.http.post<Usuario>(this.base, req);
  }

  /**
   * Carga masiva de jugadores via CSV. El campo del multipart debe llamarse `file`.
   * NO seteamos Content-Type manualmente: el navegador agrega el boundary del multipart.
   */
  importCsv(file: File): Observable<ImportResult> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ImportResult>(`${this.base}/import`, formData);
  }
}

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PagedResponse } from '../models/api.model';
import {
  UsuarioRow, UsuarioDetail, UsuarioCreateRequest, UsuarioUpdateRequest,
  RubroRow, RubroDetail, RubroRequest,
  ParamMacroRow, ParamMacroDetail, ParamMacroRequest, MacroTrimestreDto,
  ParamRubroRow, ParamRubroDetail, ParamRubroRequest, RubroTrimestreDto,
  EventoRow, EventoDetail, EventoRequest,
  EntidadRow, EntidadDetail, EntidadRequest,
  EstadoRequest,
} from '../models/admin.model';

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private http = inject(HttpClient);
  private base = '/v1/admin';

  // ── Helpers ──

  private pagedParams(page: number, size: number, q?: string, extra?: Record<string, string | number | boolean>): HttpParams {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (extra) {
      for (const [k, v] of Object.entries(extra)) {
        if (v !== undefined && v !== null && v !== '') params = params.set(k, v);
      }
    }
    return params;
  }

  // =====================================================================
  // USUARIOS
  // =====================================================================

  listUsuarios(page = 0, size = 20, q?: string, rol?: string, activo?: boolean): Observable<PagedResponse<UsuarioRow>> {
    const extra: Record<string, string | boolean> = {};
    if (rol) extra['rol'] = rol;
    if (activo !== undefined) extra['activo'] = activo;
    return this.http.get<PagedResponse<UsuarioRow>>(`${this.base}/usuarios`, { params: this.pagedParams(page, size, q, extra) });
  }

  getUsuario(id: number): Observable<UsuarioDetail> {
    return this.http.get<UsuarioDetail>(`${this.base}/usuarios/${id}`);
  }

  createUsuario(req: UsuarioCreateRequest): Observable<UsuarioDetail> {
    return this.http.post<UsuarioDetail>(`${this.base}/usuarios`, req);
  }

  updateUsuario(id: number, req: UsuarioUpdateRequest): Observable<UsuarioDetail> {
    return this.http.put<UsuarioDetail>(`${this.base}/usuarios/${id}`, req);
  }

  toggleUsuarioActivo(id: number, activo: boolean): Observable<void> {
    return this.http.patch<void>(`${this.base}/usuarios/${id}/estado`, { activo } as EstadoRequest);
  }

  // =====================================================================
  // RUBROS
  // =====================================================================

  listRubros(page = 0, size = 20, q?: string, activo?: boolean): Observable<PagedResponse<RubroRow>> {
    const extra: Record<string, boolean> = {};
    if (activo !== undefined) extra['activo'] = activo;
    return this.http.get<PagedResponse<RubroRow>>(`${this.base}/rubros`, { params: this.pagedParams(page, size, q, extra) });
  }

  getRubro(id: number): Observable<RubroDetail> {
    return this.http.get<RubroDetail>(`${this.base}/rubros/${id}`);
  }

  createRubro(req: RubroRequest): Observable<RubroDetail> {
    return this.http.post<RubroDetail>(`${this.base}/rubros`, req);
  }

  updateRubro(id: number, req: RubroRequest): Observable<RubroDetail> {
    return this.http.put<RubroDetail>(`${this.base}/rubros/${id}`, req);
  }

  toggleRubroActivo(id: number, activo: boolean): Observable<void> {
    return this.http.patch<void>(`${this.base}/rubros/${id}/estado`, { activo } as EstadoRequest);
  }

  // =====================================================================
  // PARAMETROS MACRO
  // =====================================================================

  listParamMacro(page = 0, size = 20, q?: string, activo?: boolean): Observable<PagedResponse<ParamMacroRow>> {
    const extra: Record<string, boolean> = {};
    if (activo !== undefined) extra['activo'] = activo;
    return this.http.get<PagedResponse<ParamMacroRow>>(`${this.base}/parametros-macro`, { params: this.pagedParams(page, size, q, extra) });
  }

  getParamMacro(id: number): Observable<ParamMacroDetail> {
    return this.http.get<ParamMacroDetail>(`${this.base}/parametros-macro/${id}`);
  }

  createParamMacro(req: ParamMacroRequest): Observable<ParamMacroDetail> {
    return this.http.post<ParamMacroDetail>(`${this.base}/parametros-macro`, req);
  }

  updateParamMacro(id: number, req: ParamMacroRequest): Observable<ParamMacroDetail> {
    return this.http.put<ParamMacroDetail>(`${this.base}/parametros-macro/${id}`, req);
  }

  toggleParamMacroActivo(id: number, activo: boolean): Observable<void> {
    return this.http.patch<void>(`${this.base}/parametros-macro/${id}/estado`, { activo } as EstadoRequest);
  }

  getParamMacroTrimestres(id: number): Observable<MacroTrimestreDto[]> {
    return this.http.get<MacroTrimestreDto[]>(`${this.base}/parametros-macro/${id}/trimestres`);
  }

  replaceParamMacroTrimestres(id: number, trimestres: MacroTrimestreDto[]): Observable<MacroTrimestreDto[]> {
    return this.http.put<MacroTrimestreDto[]>(`${this.base}/parametros-macro/${id}/trimestres`, trimestres);
  }

  // =====================================================================
  // PARAMETROS RUBRO
  // =====================================================================

  listParamRubro(page = 0, size = 20, q?: string, rubroId?: number, activo?: boolean): Observable<PagedResponse<ParamRubroRow>> {
    const extra: Record<string, number | boolean> = {};
    if (rubroId) extra['rubroId'] = rubroId;
    if (activo !== undefined) extra['activo'] = activo;
    return this.http.get<PagedResponse<ParamRubroRow>>(`${this.base}/parametros-rubro`, { params: this.pagedParams(page, size, q, extra) });
  }

  getParamRubro(id: number): Observable<ParamRubroDetail> {
    return this.http.get<ParamRubroDetail>(`${this.base}/parametros-rubro/${id}`);
  }

  createParamRubro(req: ParamRubroRequest): Observable<ParamRubroDetail> {
    return this.http.post<ParamRubroDetail>(`${this.base}/parametros-rubro`, req);
  }

  updateParamRubro(id: number, req: ParamRubroRequest): Observable<ParamRubroDetail> {
    return this.http.put<ParamRubroDetail>(`${this.base}/parametros-rubro/${id}`, req);
  }

  toggleParamRubroActivo(id: number, activo: boolean): Observable<void> {
    return this.http.patch<void>(`${this.base}/parametros-rubro/${id}/estado`, { activo } as EstadoRequest);
  }

  getParamRubroTrimestres(id: number): Observable<RubroTrimestreDto[]> {
    return this.http.get<RubroTrimestreDto[]>(`${this.base}/parametros-rubro/${id}/trimestres`);
  }

  replaceParamRubroTrimestres(id: number, trimestres: RubroTrimestreDto[]): Observable<RubroTrimestreDto[]> {
    return this.http.put<RubroTrimestreDto[]>(`${this.base}/parametros-rubro/${id}/trimestres`, trimestres);
  }

  // =====================================================================
  // EVENTOS CATALOGO
  // =====================================================================

  listEventos(page = 0, size = 20, q?: string, rubroId?: number, severidad?: string, activo?: boolean): Observable<PagedResponse<EventoRow>> {
    const extra: Record<string, string | number | boolean> = {};
    if (rubroId) extra['rubroId'] = rubroId;
    if (severidad) extra['severidad'] = severidad;
    if (activo !== undefined) extra['activo'] = activo;
    return this.http.get<PagedResponse<EventoRow>>(`${this.base}/eventos`, { params: this.pagedParams(page, size, q, extra) });
  }

  getEvento(id: number): Observable<EventoDetail> {
    return this.http.get<EventoDetail>(`${this.base}/eventos/${id}`);
  }

  createEvento(req: EventoRequest): Observable<EventoDetail> {
    return this.http.post<EventoDetail>(`${this.base}/eventos`, req);
  }

  updateEvento(id: number, req: EventoRequest): Observable<EventoDetail> {
    return this.http.put<EventoDetail>(`${this.base}/eventos/${id}`, req);
  }

  toggleEventoActivo(id: number, activo: boolean): Observable<void> {
    return this.http.patch<void>(`${this.base}/eventos/${id}/estado`, { activo } as EstadoRequest);
  }

  // =====================================================================
  // ENTIDADES
  // =====================================================================

  listEntidades(page = 0, size = 20, q?: string, activa?: boolean): Observable<PagedResponse<EntidadRow>> {
    const extra: Record<string, boolean> = {};
    if (activa !== undefined) extra['activa'] = activa;
    return this.http.get<PagedResponse<EntidadRow>>(`${this.base}/entidades`, { params: this.pagedParams(page, size, q, extra) });
  }

  getEntidad(id: number): Observable<EntidadDetail> {
    return this.http.get<EntidadDetail>(`${this.base}/entidades/${id}`);
  }

  createEntidad(req: EntidadRequest): Observable<EntidadDetail> {
    return this.http.post<EntidadDetail>(`${this.base}/entidades`, req);
  }

  updateEntidad(id: number, req: EntidadRequest): Observable<EntidadDetail> {
    return this.http.put<EntidadDetail>(`${this.base}/entidades/${id}`, req);
  }

  toggleEntidadActiva(id: number, activa: boolean): Observable<void> {
    return this.http.patch<void>(`${this.base}/entidades/${id}/estado`, { activo: activa } as EstadoRequest);
  }
}

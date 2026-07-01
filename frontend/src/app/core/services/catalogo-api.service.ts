import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AreaDecision, Rubro, ParametroMacro, ParametroRubro } from '../models/catalogo.model';
import { EventoCatalogo } from '../models/evento.model';

@Injectable({ providedIn: 'root' })
export class CatalogoApiService {
  private http = inject(HttpClient);

  getRubros(activo = true): Observable<Rubro[]> {
    return this.http.get<Rubro[]>('/v1/catalogos/rubros', { params: { activo } });
  }

  getRubro(id: number): Observable<Rubro & { parametros: ParametroRubro[] }> {
    return this.http.get<Rubro & { parametros: ParametroRubro[] }>(`/v1/catalogos/rubros/${id}`);
  }

  getParametrosMacro(activo = true): Observable<ParametroMacro[]> {
    return this.http.get<ParametroMacro[]>('/v1/catalogos/parametros-macro', {
      params: { activo },
    });
  }

  getParametrosMacroById(id: number): Observable<ParametroMacro> {
    return this.http.get<ParametroMacro>(`/v1/catalogos/parametros-macro/${id}`);
  }

  getParametrosRubro(rubroId?: number): Observable<ParametroRubro[]> {
    const params: Record<string, string | number> = {};
    if (rubroId) params['rubro_id'] = rubroId;
    return this.http.get<ParametroRubro[]>('/v1/catalogos/parametros-rubro', { params });
  }

  getEventosCatalogo(rubroId?: number): Observable<EventoCatalogo[]> {
    const params: Record<string, string | number> = {};
    if (rubroId) params['rubro_id'] = rubroId;
    return this.http.get<EventoCatalogo[]>('/v1/catalogos/eventos', { params });
  }

  getAreas(): Observable<AreaDecision[]> {
    return this.http.get<AreaDecision[]>('/v1/catalogos/areas');
  }
}

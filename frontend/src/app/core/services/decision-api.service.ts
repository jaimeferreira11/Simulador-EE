import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Decision, DecisionInput, ValidacionDecision } from '../models/decision.model';
import { ContextoDecision, ProyeccionFinanciera } from '../models/contexto-decision.model';

@Injectable({ providedIn: 'root' })
export class DecisionApiService {
  private http = inject(HttpClient);

  listByTrimestre(trimestreId: number): Observable<Decision[]> {
    return this.http.get<Decision[]>(`/v1/trimestres/${trimestreId}/decisiones`);
  }

  get(equipoId: number, trimestreId: number): Observable<Decision> {
    return this.http.get<Decision>(
      `/v1/equipos/${equipoId}/trimestres/${trimestreId}/decision`
    );
  }

  upsert(equipoId: number, trimestreId: number, data: DecisionInput): Observable<Decision> {
    return this.http.put<Decision>(
      `/v1/equipos/${equipoId}/trimestres/${trimestreId}/decision`,
      data
    );
  }

  validar(equipoId: number, trimestreId: number, data: DecisionInput): Observable<ValidacionDecision> {
    return this.http.post<ValidacionDecision>(
      `/v1/equipos/${equipoId}/trimestres/${trimestreId}/decision/validar`,
      data
    );
  }

  enviar(equipoId: number, trimestreId: number): Observable<Decision> {
    return this.http.post<Decision>(
      `/v1/equipos/${equipoId}/trimestres/${trimestreId}/decision/enviar`,
      {}
    );
  }

  reabrir(equipoId: number, trimestreId: number): Observable<Decision> {
    return this.http.post<Decision>(
      `/v1/equipos/${equipoId}/trimestres/${trimestreId}/decision/reabrir`,
      {}
    );
  }

  getContextoDecision(equipoId: number, trimestreId: number): Observable<ContextoDecision> {
    return this.http.get<ContextoDecision>(
      `/v1/equipos/${equipoId}/trimestres/${trimestreId}/decision/contexto`
    );
  }

  getProyeccion(equipoId: number, trimestreId: number, input: DecisionInput): Observable<ProyeccionFinanciera> {
    return this.http.post<ProyeccionFinanciera>(
      `/v1/equipos/${equipoId}/trimestres/${trimestreId}/decision/proyeccion`,
      input
    );
  }
}

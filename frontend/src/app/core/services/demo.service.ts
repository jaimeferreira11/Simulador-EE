import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Competencia } from '../models/competencia.model';
import { Decision } from '../models/decision.model';

export interface AvanzarResult {
  trimestreAnteriorId: number;
  trimestreActualId: number | null;
  competenciaEstado: 'EN_CURSO' | 'FINALIZADA' | string;
}

const STORAGE_KEY = 'demoCeoModeActivo';
const DEMO_CODIGO = 'DEMO';

@Injectable({ providedIn: 'root' })
export class DemoService {
  private http = inject(HttpClient);

  /** True iff this competencia is the special DEMO competition. */
  isDemo(comp: Competencia | null | undefined): boolean {
    return !!comp && comp.codigo === DEMO_CODIGO;
  }

  private modoCeoActivoSignal = signal<boolean>(this.readStoredFlag());
  readonly modoCeoActivo = computed(() => this.modoCeoActivoSignal());

  setModoCeoActivo(activo: boolean): void {
    this.modoCeoActivoSignal.set(activo);
    if (typeof window === 'undefined') return;
    if (activo) {
      sessionStorage.setItem(STORAGE_KEY, 'true');
    } else {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  }

  reiniciar(competenciaId: number): Observable<Competencia> {
    return this.http.post<Competencia>(`/v1/competencias/${competenciaId}/demo/reiniciar`, {});
  }

  decisionCeo(competenciaId: number, payload: Record<string, unknown>): Observable<Decision> {
    return this.http.post<Decision>(`/v1/competencias/${competenciaId}/demo/decision-ceo`, payload);
  }

  avanzar(competenciaId: number): Observable<AvanzarResult> {
    return this.http.post<AvanzarResult>(`/v1/competencias/${competenciaId}/demo/avanzar`, {});
  }

  private readStoredFlag(): boolean {
    if (typeof window === 'undefined') return false;
    return sessionStorage.getItem(STORAGE_KEY) === 'true';
  }
}

import { Injectable, inject, signal, computed } from '@angular/core';
import { CompetenciaApiService } from '../services/competencia-api.service';
import { CompetenciaDetalle } from '../models/competencia.model';
import { firstValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class CompetenciaStore {
  private api = inject(CompetenciaApiService);

  competenciaActiva = signal<CompetenciaDetalle | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);

  trimestres = computed(() => this.competenciaActiva()?.trimestres ?? []);
  equipos = computed(() => this.competenciaActiva()?.equipos ?? []);
  estado = computed(() => this.competenciaActiva()?.estado ?? null);

  trimestreActual = computed(() => {
    const trimestres = this.trimestres();
    if (!trimestres.length) return null;
    const noProcessed = trimestres.find(t => t.estado !== 'PROCESADO' && t.estado !== 'ANULADO');
    return noProcessed ?? trimestres[trimestres.length - 1];
  });

  async load(id: number): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const detalle = await firstValueFrom(this.api.getById(id));
      this.competenciaActiva.set(detalle);
    } catch (e: any) {
      this.error.set(e?.error?.detail ?? 'Error al cargar la competencia');
    } finally {
      this.loading.set(false);
    }
  }

  async reload(): Promise<void> {
    const id = this.competenciaActiva()?.id;
    if (id) await this.load(id);
  }

  clear(): void {
    this.competenciaActiva.set(null);
    this.error.set(null);
  }
}

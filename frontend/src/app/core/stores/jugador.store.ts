import { Injectable, inject, signal, computed } from '@angular/core';
import { forkJoin } from 'rxjs';
import { AuthStore } from './auth.store';
import { CompetenciaStore } from './competencia.store';
import { CompetenciaApiService } from '../services/competencia-api.service';
import { EquipoApiService } from '../services/equipo-api.service';
import { EquipoDetalle } from '../models/equipo.model';
import { Competencia } from '../models/competencia.model';

/**
 * Resolves the jugador's active context: which competencia they're in
 * and which equipo they belong to.
 */
@Injectable({ providedIn: 'root' })
export class JugadorStore {
  private authStore = inject(AuthStore);
  private competenciaStore = inject(CompetenciaStore);
  private competenciaApi = inject(CompetenciaApiService);
  private equipoApi = inject(EquipoApiService);

  equipo = signal<EquipoDetalle | null>(null);
  misCompetencias = signal<Competencia[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  competencia = this.competenciaStore.competenciaActiva;
  trimestres = this.competenciaStore.trimestres;
  trimestreActual = this.competenciaStore.trimestreActual;

  equipoId = computed(() => this.equipo()?.id ?? null);
  nombreEquipo = computed(() => this.equipo()?.nombre_empresa ?? null);
  esCapitan = computed(() => {
    const eq = this.equipo();
    const userId = this.authStore.user()?.id;
    if (!eq || !userId) return false;
    return eq.miembros.some(m => m.usuario_id === userId && m.es_capitan);
  });

  /**
   * Load the jugador's competencias, auto-select the first EN_CURSO,
   * then resolve which equipo the jugador belongs to.
   */
  async init(): Promise<void> {
    // If competencia is loaded but equipo isn't, resolve equipo
    if (this.competencia() && this.equipo()) return;
    if (this.competencia() && !this.equipo()) {
      this.loading.set(true);
      try {
        await this.resolveEquipo();
      } finally {
        this.loading.set(false);
      }
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    try {
      // 1. Get jugador's competencias (backend filters by JWT)
      const page = await new Promise<any>((resolve, reject) =>
        this.competenciaApi.list(0, 50).subscribe({ next: resolve, error: reject })
      );
      const competencias: Competencia[] = page.content;
      this.misCompetencias.set(competencias);

      // 2. Find the active one (EN_CURSO preferred, otherwise first)
      const activa = competencias.find(c => c.estado === 'EN_CURSO')
        ?? competencias.find(c => c.estado === 'PENDIENTE_FINALIZAR')
        ?? competencias.find(c => c.estado === 'FINALIZADA')
        ?? competencias.find(c => c.estado === 'ABIERTA_INSCRIPCION')
        ?? competencias[0];

      if (!activa) {
        this.loading.set(false);
        return;
      }

      // 3. Load full competencia detail into CompetenciaStore
      await this.competenciaStore.load(activa.id);

      // 4. Resolve equipo: check each equipo's members to find ours
      await this.resolveEquipo();
    } catch (e: any) {
      this.error.set(e?.error?.detail ?? 'Error al cargar contexto del jugador');
    } finally {
      this.loading.set(false);
    }
  }

  async resolveEquipo(): Promise<void> {
    const comp = this.competencia();
    const userId = this.authStore.user()?.id;
    if (!comp || !userId) return;

    const equipos = comp.equipos;
    if (!equipos.length) return;

    // Load details for all equipos to find the one containing our user
    const details$ = equipos.map(e => this.equipoApi.getById(e.id));
    const details = await new Promise<EquipoDetalle[]>((resolve, reject) =>
      forkJoin(details$).subscribe({ next: resolve, error: reject })
    );

    const miEquipo = details.find(eq =>
      eq.miembros.some(m => m.usuario_id === userId)
    );

    if (miEquipo) {
      this.equipo.set(miEquipo);
    }
  }

  /** Switch to a different competencia */
  async switchCompetencia(competenciaId: number): Promise<void> {
    this.loading.set(true);
    this.equipo.set(null);
    // Reset confetti flags so the winner animation shows again for the new context
    Object.keys(sessionStorage)
      .filter(k => k.startsWith('confetti_shown_'))
      .forEach(k => sessionStorage.removeItem(k));
    try {
      await this.competenciaStore.load(competenciaId);
      await this.resolveEquipo();
    } catch (e: any) {
      this.error.set(e?.error?.detail ?? 'Error al cambiar competencia');
    } finally {
      this.loading.set(false);
    }
  }

  clear(): void {
    this.equipo.set(null);
    this.misCompetencias.set([]);
    this.competenciaStore.clear();
  }
}

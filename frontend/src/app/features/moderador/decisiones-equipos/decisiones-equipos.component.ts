import { Component, inject, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule, Timer, Lock, Check, Eye } from 'lucide-angular';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { CompetenciaApiService } from '../../../core/services/competencia-api.service';
import { DecisionApiService } from '../../../core/services/decision-api.service';
import { EquipoApiService } from '../../../core/services/equipo-api.service';
import { Decision } from '../../../core/models/decision.model';
import { EquipoDetalle } from '../../../core/models/equipo.model';
import { CompetenciaDetalle } from '../../../core/models/competencia.model';
import { Trimestre } from '../../../core/models/trimestre.model';
import { AREA_COLORS, AREA_LABELS, CAMPOS_POR_AREA, AreaDecisionV2 } from '../../../core/models/contexto-decision.model';
import { forkJoin, Subscription } from 'rxjs';
import { distinctUntilChanged, map } from 'rxjs/operators';

interface AreaProgreso {
  area: AreaDecisionV2;
  label: string;
  completos: number;
  total: number;
  color: string;
}

interface EquipoDecisionInfo {
  equipo: EquipoDetalle;
  decision: Decision | null;
  estado: string;
  camposCompletos: number;
  totalCampos: number;
  progresoPorArea: AreaProgreso[];
  footerText: string;
  borderColor: string;
}

@Component({
  selector: 'app-decisiones-equipos',
  standalone: true,
  imports: [LucideAngularModule, RouterLink],
  templateUrl: './decisiones-equipos.component.html',
})
export class DecisionesEquiposComponent implements OnInit, OnDestroy {
  private competenciaStore = inject(CompetenciaStore);
  private competenciaApi = inject(CompetenciaApiService);
  private decisionApi = inject(DecisionApiService);
  private equipoApi = inject(EquipoApiService);
  private route = inject(ActivatedRoute);

  loading = signal(true);
  error = signal<string | null>(null);
  equiposInfo = signal<EquipoDecisionInfo[]>([]);

  // Local signals so we don't trust stale store data after navigation.
  competencia = signal<CompetenciaDetalle | null>(null);
  trimestreActual = signal<Trimestre | null>(null);

  readonly areaColors = AREA_COLORS;
  readonly areaLabels = AREA_LABELS;
  readonly icons = { Timer, Lock, Check, Eye };

  totalEquipos = computed(() => this.equiposInfo().length);
  enviadas = computed(() => this.equiposInfo().filter(e => e.estado === 'ENVIADA').length);
  borrador = computed(() => this.equiposInfo().filter(e => e.estado === 'BORRADOR').length);
  sinIniciar = computed(() => this.equiposInfo().filter(e => e.estado === 'SIN_CREAR').length);

  cierreEn = computed(() => {
    const trim = this.trimestreActual();
    if (!trim?.cierre_at) return null;
    const diff = new Date(trim.cierre_at).getTime() - Date.now();
    if (diff <= 0) return 'Vencido';
    const d = Math.floor(diff / 86400000);
    const h = Math.floor((diff % 86400000) / 3600000);
    return `${d}d ${h}h`;
  });

  hasTrimestre = computed(() => this.trimestreActual() !== null);

  private routeSub?: Subscription;

  ngOnInit(): void {
    this.routeSub = this.route.queryParamMap
      .pipe(
        map(params => params.get('competencia')),
        distinctUntilChanged(),
      )
      .subscribe(competenciaParam => {
        const competenciaId = competenciaParam ? Number(competenciaParam) : null;
        this.resetState();
        this.bootstrap(competenciaId);
      });
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
  }

  private resetState(): void {
    this.loading.set(true);
    this.error.set(null);
    this.equiposInfo.set([]);
    this.competencia.set(null);
    this.trimestreActual.set(null);
  }

  private async bootstrap(competenciaId: number | null): Promise<void> {
    try {
      let comp: CompetenciaDetalle | null = null;

      if (competenciaId !== null) {
        const stored = this.competenciaStore.competenciaActiva();
        if (stored && stored.id === competenciaId) {
          comp = stored;
        } else {
          // Different competencia in store (or empty) -- fetch fresh and update store.
          await this.competenciaStore.load(competenciaId);
          comp = this.competenciaStore.competenciaActiva();
        }
      } else {
        // No query param -- fall back to whatever is in the store.
        comp = this.competenciaStore.competenciaActiva();
      }

      if (!comp) {
        this.error.set('No se pudo cargar la competencia seleccionada');
        this.loading.set(false);
        return;
      }

      this.competencia.set(comp);

      // Compute the current trimestre from the freshly loaded competencia,
      // not from the store (which can lag behind navigation).
      const trim = this.computeTrimestreActual(comp);
      this.trimestreActual.set(trim);

      if (!trim) {
        // No trimestres available (typical for BORRADOR competencias).
        // Not an error -- the template renders an explicit empty state.
        this.loading.set(false);
        return;
      }

      this.loadEquiposYDecisiones(comp, trim);
    } catch {
      this.error.set('Error al cargar la competencia');
      this.loading.set(false);
    }
  }

  private computeTrimestreActual(comp: CompetenciaDetalle): Trimestre | null {
    const trimestres = comp.trimestres ?? [];
    if (!trimestres.length) return null;
    const noProcessed = trimestres.find(t => t.estado !== 'PROCESADO' && t.estado !== 'ANULADO');
    return noProcessed ?? trimestres[trimestres.length - 1];
  }

  private loadEquiposYDecisiones(comp: CompetenciaDetalle, trim: Trimestre): void {
    if (!comp.equipos?.length) {
      this.equiposInfo.set([]);
      this.loading.set(false);
      return;
    }

    const equipos$ = forkJoin(comp.equipos.map(e => this.equipoApi.getById(e.id)));
    const decisiones$ = this.decisionApi.listByTrimestre(trim.id);

    forkJoin({ decisiones: decisiones$, equipos: equipos$ }).subscribe({
      next: ({ decisiones, equipos }) => {
        const areas: AreaDecisionV2[] = ['COMERCIAL', 'OPERACIONES', 'TALENTO_HUMANO', 'FINANZAS'];

        const infos: EquipoDecisionInfo[] = equipos.map(eq => {
          const dec = decisiones.find(d => d.equipo_id === eq.id) ?? null;
          const estado = dec ? dec.estado : 'SIN_CREAR';

          let camposCompletos = 0;
          const progresoPorArea: AreaProgreso[] = areas.map(area => {
            const campos = CAMPOS_POR_AREA[area];
            const completos = dec
              ? campos.filter(c => (dec as any)[c.field] != null && (dec as any)[c.field] !== 0).length
              : 0;
            camposCompletos += completos;
            return {
              area,
              label: area === 'TALENTO_HUMANO' ? 'RRHH' : AREA_LABELS[area],
              completos,
              total: campos.length,
              color: completos === campos.length ? '#16A34A' : completos > 0 ? '#F59E0B' : '#9CA3AF',
            };
          });

          const allFields = areas.flatMap(a => CAMPOS_POR_AREA[a]);

          return {
            equipo: eq,
            decision: dec,
            estado,
            camposCompletos,
            totalCampos: allFields.length,
            progresoPorArea,
            footerText: '',
            borderColor: this.borderColorForEstado(estado),
          };
        });

        this.equiposInfo.set(infos);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Error al cargar las decisiones de los equipos');
        this.loading.set(false);
      },
    });
  }

  private borderColorForEstado(estado: string): string {
    switch (estado) {
      case 'ENVIADA': case 'PROCESADA': return '#16A34A';
      case 'BORRADOR': return '#F59E0B';
      case 'SIN_CREAR': return '#DC2626';
      default: return '#E2E4E8';
    }
  }

  estadoLabel(estado: string): string {
    switch (estado) {
      case 'ENVIADA': return 'ENVIADA';
      case 'BORRADOR': return 'BORRADOR';
      case 'SIN_CREAR': return 'SIN INICIAR';
      default: return estado;
    }
  }

  estadoBadgeBg(estado: string): string {
    switch (estado) {
      case 'ENVIADA': case 'PROCESADA': return '#DCFCE7';
      case 'BORRADOR': return '#FEF3C7';
      case 'SIN_CREAR': return '#FEE2E2';
      default: return '#F7F8FA';
    }
  }

  estadoBadgeColor(estado: string): string {
    switch (estado) {
      case 'ENVIADA': case 'PROCESADA': return '#16A34A';
      case 'BORRADOR': return '#92400E';
      case 'SIN_CREAR': return '#DC2626';
      default: return '#6B7280';
    }
  }

  progressColor(info: EquipoDecisionInfo): string {
    if (info.camposCompletos === info.totalCampos) return '#16A34A';
    if (info.camposCompletos > 0) return '#F59E0B';
    return '#DC2626';
  }

  progressTextColor(info: EquipoDecisionInfo): string {
    return this.progressColor(info);
  }

  footerTextColor(estado: string): string {
    return estado === 'SIN_CREAR' ? '#DC2626' : '#7A7A7A';
  }
}

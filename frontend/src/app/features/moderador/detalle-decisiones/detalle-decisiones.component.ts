import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { LucideAngularModule, ArrowLeft, FileX, RotateCcw } from 'lucide-angular';
import { forkJoin, catchError, of } from 'rxjs';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { DecisionApiService } from '../../../core/services/decision-api.service';
import { EquipoApiService } from '../../../core/services/equipo-api.service';
import { Decision } from '../../../core/models/decision.model';
import { EquipoDetalle } from '../../../core/models/equipo.model';
import { CAMPOS_POR_AREA, AREA_COLORS, AREA_LABELS, AreaDecisionV2 } from '../../../core/models/contexto-decision.model';

interface FilaDetalle {
  area: AreaDecisionV2;
  areaLabel: string;
  campo: string;
  field: string;
  valor: string;
  valorAnterior: string;
  delta: string;
  deltaPositive: boolean;
  editadoPor: string;
  unidad: string;
}

@Component({
  selector: 'app-detalle-decisiones',
  standalone: true,
  imports: [DatePipe, LucideAngularModule, RouterLink],
  templateUrl: './detalle-decisiones.component.html',
})
export class DetalleDecisionesComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private competenciaStore = inject(CompetenciaStore);
  private decisionApi = inject(DecisionApiService);
  private equipoApi = inject(EquipoApiService);

  loading = signal(true);
  error = signal<string | null>(null);
  equipo = signal<EquipoDetalle | null>(null);
  decision = signal<Decision | null>(null);
  decisionAnterior = signal<Decision | null>(null);
  reopening = signal(false);
  trimestreNumero = signal<number>(0);
  capitan = signal<string>('');

  trimestreActual = this.competenciaStore.trimestreActual;

  readonly icons = { ArrowLeft, FileX, RotateCcw };
  readonly areaColors = AREA_COLORS;

  filas = computed<FilaDetalle[]>(() => {
    const dec = this.decision();
    if (!dec) return [];

    const ant = this.decisionAnterior();
    const areas: AreaDecisionV2[] = ['COMERCIAL', 'OPERACIONES', 'TALENTO_HUMANO', 'FINANZAS'];
    const result: FilaDetalle[] = [];

    for (const area of areas) {
      for (const campo of CAMPOS_POR_AREA[area]) {
        const rawVal = (dec as any)[campo.field];
        const anterior = ant ? (ant as any)[campo.field] ?? null : null;

        const valor = this.formatVal(rawVal, campo.unidad);
        const valorAnterior = this.formatVal(anterior, campo.unidad);

        let delta = '';
        let deltaPositive = true;
        if (rawVal != null && anterior != null) {
          const diff = rawVal - anterior;
          if (diff !== 0) {
            deltaPositive = diff > 0;
            const prefix = diff > 0 ? '+' : '';
            delta = prefix + this.formatVal(diff, campo.unidad);
          }
        }

        result.push({
          area,
          areaLabel: AREA_LABELS[area],
          campo: campo.label,
          field: campo.field,
          valor,
          valorAnterior,
          delta,
          deltaPositive,
          editadoPor: '—',
          unidad: campo.unidad,
        });
      }
    }
    return result;
  });

  private formatVal(val: number | null, unidad: string): string {
    if (val == null) return '—';
    if (unidad === 'guarani') return '₲ ' + val.toLocaleString('es-PY');
    if (unidad === 'porcentaje') return val + '%';
    if (unidad === 'personas') return val + ' personas';
    return val.toLocaleString('es-PY') + ' uds';
  }

  ngOnInit(): void {
    const equipoId = Number(this.route.snapshot.paramMap.get('equipoId'));
    this.loadData(equipoId);
  }

  private loadData(equipoId: number): void {
    const trim = this.trimestreActual();
    if (!trim) {
      this.error.set('No hay un trimestre activo seleccionado');
      this.loading.set(false);
      return;
    }

    this.trimestreNumero.set(trim.numero);

    forkJoin({
      equipo: this.equipoApi.getById(equipoId),
      decision: this.decisionApi.get(equipoId, trim.id).pipe(catchError(() => of(null))),
    }).subscribe({
      next: ({ equipo, decision }) => {
        this.equipo.set(equipo);
        this.decision.set(decision);

        // Buscar capitan
        const cap = equipo.miembros?.find((m: any) => m.es_capitan);
        if (cap) this.capitan.set(cap.usuario?.nombre_completo ?? '');

        // Cargar decision anterior si hay Q previo
        if (trim.numero > 1) {
          this.loadDecisionAnterior(equipoId, trim);
        } else {
          this.loading.set(false);
        }
      },
      error: () => {
        this.error.set('Error al cargar los datos del equipo');
        this.loading.set(false);
      },
    });
  }

  private loadDecisionAnterior(equipoId: number, trim: any): void {
    const comp = this.competenciaStore.competenciaActiva();
    if (!comp) {
      this.loading.set(false);
      return;
    }

    const trimAnterior = comp.trimestres?.find((t: any) => t.numero === trim.numero - 1);
    if (!trimAnterior) {
      this.loading.set(false);
      return;
    }

    this.decisionApi.get(equipoId, trimAnterior.id).pipe(
      catchError(() => of(null))
    ).subscribe({
      next: (dec) => {
        this.decisionAnterior.set(dec);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  reabrir(): void {
    const eq = this.equipo();
    const trim = this.trimestreActual();
    if (!eq || !trim) return;

    this.reopening.set(true);
    this.decisionApi.reabrir(eq.id, trim.id).subscribe({
      next: (d) => {
        this.decision.set(d);
        this.reopening.set(false);
      },
      error: () => this.reopening.set(false),
    });
  }

  estadoColor(estado: string): string {
    switch (estado) {
      case 'ENVIADA': case 'PROCESADA': return '#16A34A';
      case 'BORRADOR': return '#D97706';
      default: return '#6B7280';
    }
  }

  estadoBg(estado: string): string {
    switch (estado) {
      case 'ENVIADA': case 'PROCESADA': return '#DCFCE7';
      case 'BORRADOR': return '#FEF3C7';
      default: return '#F7F8FA';
    }
  }
}

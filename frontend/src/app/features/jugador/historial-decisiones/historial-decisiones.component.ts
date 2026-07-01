import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { JugadorStore } from '../../../core/stores/jugador.store';
import { DecisionApiService } from '../../../core/services/decision-api.service';
import { Decision } from '../../../core/models/decision.model';
import { GuaraniPipe } from '../../../core/pipes/guarani.pipe';

interface DecisionRow {
  fecha: string;
  departamento: string;
  badgeClass: string;
  autor: string;
  decision: string;
  valor: string;
}

const FIELD_META: Record<
  string,
  { label: string; dept: string; badge: string; format: 'guarani' | 'number' | 'pct' }
> = {
  precio_venta: {
    label: 'Precio de Venta',
    dept: 'Comercial',
    badge: 'sim-badge-comercial',
    format: 'guarani',
  },
  inversion_marketing: {
    label: 'Inversión Marketing',
    dept: 'Comercial',
    badge: 'sim-badge-comercial',
    format: 'guarani',
  },
  produccion_planificada: {
    label: 'Producción Planificada',
    dept: 'Operaciones',
    badge: 'sim-badge-operaciones',
    format: 'number',
  },
  inversion_capacidad: {
    label: 'Inversión Capacidad',
    dept: 'Operaciones',
    badge: 'sim-badge-operaciones',
    format: 'guarani',
  },
  contrataciones_netas: {
    label: 'Contrataciones Netas',
    dept: 'RRHH',
    badge: 'sim-badge-rrhh',
    format: 'number',
  },
  aumento_salarial_pct: {
    label: 'Aumento Salarial',
    dept: 'RRHH',
    badge: 'sim-badge-rrhh',
    format: 'pct',
  },
  inversion_capacitacion: {
    label: 'Inversión Capacitación',
    dept: 'RRHH',
    badge: 'sim-badge-rrhh',
    format: 'guarani',
  },
  prestamo_solicitado: {
    label: 'Préstamo Solicitado',
    dept: 'Finanzas',
    badge: 'sim-badge-finanzas',
    format: 'guarani',
  },
  // dividendos_pagar omitido del flujo del jugador (siempre 0).
  inversion_id: {
    label: 'Inversión I+D',
    dept: 'Innovación',
    badge: 'sim-badge-innovacion',
    format: 'guarani',
  },
};

@Component({
  selector: 'app-historial-decisiones',
  standalone: true,
  imports: [],
  templateUrl: './historial-decisiones.component.html',
})
export class HistorialDecisionesComponent implements OnInit {
  private jugadorStore = inject(JugadorStore);
  private decisionApi = inject(DecisionApiService);
  private guaraniPipe = new GuaraniPipe();

  loading = signal(true);
  decisions = signal<Decision[]>([]);
  trimestreSeleccionado = signal<number>(1);

  competencia = this.jugadorStore.competencia;
  equipo = this.jugadorStore.equipo;
  nombreEquipo = this.jugadorStore.nombreEquipo;

  trimestresDisponibles = computed(() => {
    return this.jugadorStore
      .trimestres()
      .filter((t) => t.estado === 'PROCESADO' || t.estado === 'ABIERTO_DECISIONES')
      .map((t) => t.numero);
  });

  // Flatten decisions into per-field rows
  decisiones = computed(() => {
    const decs = this.decisions();
    const miembros = this.equipo()?.miembros ?? [];
    const rows: DecisionRow[] = [];

    for (const d of decs) {
      const fecha = d.submitted_at
        ? new Date(d.submitted_at).toLocaleDateString('es-PY', {
            day: '2-digit',
            month: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
          })
        : new Date(d.updated_at).toLocaleDateString('es-PY', {
            day: '2-digit',
            month: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
          });

      // Resolve author name from equipo members
      const miembro = miembros.find((m) => m.usuario_id === d.registrado_por_usuario_id);
      const autor = miembro?.usuario.nombre_completo ?? '—';

      for (const [field, meta] of Object.entries(FIELD_META)) {
        const val = (d as any)[field];
        if (val == null || val === 0) continue;

        rows.push({
          fecha,
          departamento: meta.dept,
          badgeClass: meta.badge,
          autor,
          decision: meta.label,
          valor: this.formatValue(val, meta.format),
        });
      }
    }

    return rows;
  });

  ngOnInit(): void {
    this.initData();
  }

  private async initData(): Promise<void> {
    await this.jugadorStore.init();

    const disponibles = this.trimestresDisponibles();
    if (disponibles.length) {
      this.trimestreSeleccionado.set(disponibles[disponibles.length - 1]);
    }

    this.loadDecisions();
  }

  selectTrimestre(num: number): void {
    this.trimestreSeleccionado.set(num);
    this.loadDecisions();
  }

  private loadDecisions(): void {
    const num = this.trimestreSeleccionado();
    const trim = this.jugadorStore.trimestres().find((t) => t.numero === num);
    const eq = this.equipo();
    if (!trim || !eq) {
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.decisionApi.get(eq.id, trim.id).subscribe({
      next: (dec) => {
        this.decisions.set([dec]);
        this.loading.set(false);
      },
      error: () => {
        this.decisions.set([]);
        this.loading.set(false);
      },
    });
  }

  private formatValue(val: number, format: 'guarani' | 'number' | 'pct'): string {
    switch (format) {
      case 'guarani':
        return this.guaraniPipe.transform(val);
      case 'number':
        return val.toLocaleString('es-PY');
      case 'pct':
        return `${val}%`;
    }
  }
}

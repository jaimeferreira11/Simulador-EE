import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { LucideAngularModule, Crown, BarChart3 } from 'lucide-angular';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';
import { JugadorStore } from '../../../core/stores/jugador.store';
import { ResultadoApiService } from '../../../core/services/resultado-api.service';
import { RankingItem, EvolucionEquipo } from '../../../core/models/resultado.model';
import { Equipo } from '../../../core/models/equipo.model';
import { GuaraniCortoPipe } from '../../../core/pipes/guarani-corto.pipe';
import { BotBadgeComponent } from '../../../shared/components/bot-badge/bot-badge.component';

const CHART_COLORS = [
  '#006B3F',
  '#F47920',
  '#2563EB',
  '#DC2626',
  '#8B5CF6',
  '#059669',
  '#D97706',
  '#EC4899',
];

export type DeptTab = 'general' | 'gestion' | 'marketing' | 'finanzas';

interface DeptTabDef {
  key: DeptTab;
  label: string;
  metricLabel: string;
  sortFn: (a: RankingItem, b: RankingItem) => number;
  formatFn: (r: RankingItem) => string;
}

@Component({
  selector: 'app-rankings',
  standalone: true,
  imports: [LucideAngularModule, BaseChartDirective, BotBadgeComponent],
  templateUrl: './rankings.component.html',
})
export class RankingsComponent implements OnInit {
  readonly icons = { Crown, BarChart3 };

  private jugadorStore = inject(JugadorStore);
  private resultadoApi = inject(ResultadoApiService);
  private guaraniPipe = new GuaraniCortoPipe();

  loading = signal(true);
  ranking = signal<RankingItem[]>([]);
  evolucion = signal<EvolucionEquipo[]>([]);
  vistaActual: 'general' | 'trimestre' = 'trimestre';
  deptActual = signal<DeptTab>('general');

  readonly deptTabs: DeptTabDef[] = [
    {
      key: 'general',
      label: 'General',
      // Criterio del ranking: utilidad acumulada (el PIP queda como índice secundario).
      metricLabel: 'Utilidad Acumulada',
      sortFn: (a, b) => b.utilidad_acumulada - a.utilidad_acumulada,
      formatFn: (r) => this.guaraniPipe.transform(r.utilidad_acumulada),
    },
    {
      key: 'gestion',
      label: 'Gestión (PIP)',
      metricLabel: 'PIP Acumulado',
      sortFn: (a, b) => b.pip_acumulado - a.pip_acumulado,
      formatFn: (r) => r.pip_acumulado.toFixed(2) + ' PIP',
    },
    {
      key: 'marketing',
      label: 'Marketing',
      metricLabel: 'Market Share',
      sortFn: (a, b) => b.share_actual - a.share_actual,
      formatFn: (r) => (r.share_actual * 100).toFixed(1) + '%',
    },
    {
      key: 'finanzas',
      label: 'Finanzas',
      metricLabel: 'Caja',
      sortFn: (a, b) => b.caja_actual - a.caja_actual,
      formatFn: (r) => this.guaraniPipe.transform(r.caja_actual),
    },
  ];

  /** Mapa equipoId → Equipo para localizar metadatos (tipo BOT) por id. */
  equipoMap = computed<Map<number, Equipo>>(() => {
    const equipos = this.competencia()?.equipos ?? [];
    return new Map(equipos.map((e) => [e.id, e]));
  });

  deptRanking = computed(() => {
    const tab = this.deptTabs.find((t) => t.key === this.deptActual()) ?? this.deptTabs[0];
    const eq = this.equipo();
    const sorted = [...this.ranking()].sort(tab.sortFn);
    const map = this.equipoMap();
    return sorted.map((r, idx) => {
      const meta = map.get(r.equipo_id);
      return {
        pos: idx + 1,
        equipo_id: r.equipo_id,
        nombre: r.nombre_empresa,
        iniciales: r.nombre_empresa
          .split(' ')
          .filter(Boolean)
          .map((p) => p[0])
          .join('')
          .substring(0, 2)
          .toUpperCase(),
        metricValue: tab.formatFn(r),
        highlight: eq ? r.equipo_id === eq.id : false,
        tipo: meta?.tipo,
        dificultad: meta?.dificultad,
        personalidad: meta?.personalidad,
      };
    });
  });

  activeDeptTab = computed(
    () => this.deptTabs.find((t) => t.key === this.deptActual()) ?? this.deptTabs[0],
  );

  competencia = this.jugadorStore.competencia;
  equipo = this.jugadorStore.equipo;
  trimestres = this.jugadorStore.trimestres;

  competenciaNombre = computed(() => this.competencia()?.nombre ?? '');

  // How many quarters have been processed
  trimestresProcessados = computed(
    () => this.trimestres().filter((t) => t.estado === 'PROCESADO').length,
  );

  quarterColumns = computed(() => {
    const total = this.competencia()?.num_trimestres ?? 4;
    return Array.from({ length: total }, (_, i) => i + 1);
  });

  // Podio: top 3 (order: 2nd, 1st, 3rd for visual layout)
  podio = computed(() => {
    const items = this.ranking();
    const first = items.find((r) => r.posicion === 1);
    const second = items.find((r) => r.posicion === 2);
    const third = items.find((r) => r.posicion === 3);
    return [
      second ? this.toPodioItem(second) : null,
      first ? this.toPodioItem(first) : null,
      third ? this.toPodioItem(third) : null,
    ];
  });

  // Full table with per-quarter PIP from evolution
  equiposTabla = computed(() => {
    const eq = this.equipo();
    const evo = this.evolucion();
    const quarters = this.quarterColumns();
    const map = this.equipoMap();

    return this.ranking().map((r) => {
      const serie = evo.find((e) => e.equipo_id === r.equipo_id)?.serie_pip ?? [];
      const qValues = quarters.map((q) => {
        const punto = serie.find((s) => s.trimestre === q);
        return punto != null ? punto.pip.toFixed(2) : '-';
      });

      const meta = map.get(r.equipo_id);
      return {
        pos: r.posicion,
        nombre: r.nombre_empresa,
        iniciales: r.nombre_empresa
          .split(' ')
          .filter(Boolean)
          .map((p) => p[0])
          .join('')
          .substring(0, 2)
          .toUpperCase(),
        // Total del ranking = utilidad acumulada (criterio). Las columnas por Q muestran el PIP.
        total: this.guaraniPipe.transform(r.utilidad_acumulada),
        highlight: eq ? r.equipo_id === eq.id : false,
        qValues,
        tipo: meta?.tipo,
        dificultad: meta?.dificultad,
        personalidad: meta?.personalidad,
      };
    });
  });

  chartConfig = computed<ChartConfiguration<'line'>>(() => {
    const evo = this.evolucion();
    const labels = this.quarterColumns().map((n) => `Q${n}`);
    return {
      type: 'line',
      data: {
        labels,
        datasets: evo.map((e, i) => ({
          label: e.nombre_empresa,
          data: labels.map((l) => {
            const q = parseInt(l.substring(1), 10);
            return e.serie_pip.find((s) => s.trimestre === q)?.pip ?? null;
          }),
          borderColor: e.codigo_color || CHART_COLORS[i % CHART_COLORS.length],
          backgroundColor: 'transparent',
          tension: 0.3,
          pointRadius: 4,
          pointHoverRadius: 6,
        })),
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: 'bottom', labels: { font: { family: 'Inter', size: 12 } } },
        },
        scales: {
          y: { title: { display: true, text: 'PIP' }, beginAtZero: true },
          x: { title: { display: true, text: 'Trimestre' } },
        },
      },
    };
  });

  ngOnInit(): void {
    this.initData();
  }

  private async initData(): Promise<void> {
    await this.jugadorStore.init();
    this.loadData();
  }

  private loadData(): void {
    const comp = this.competencia();
    if (!comp) {
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.loadCount = 0;

    // Load ranking and evolution in parallel
    this.resultadoApi.getRanking(comp.id).subscribe({
      next: (items) => {
        this.ranking.set(items);
        this.checkLoaded();
      },
      error: () => this.checkLoaded(),
    });

    this.resultadoApi.getEvolucion(comp.id).subscribe({
      next: (items) => {
        this.evolucion.set(items);
        this.checkLoaded();
      },
      error: () => this.checkLoaded(),
    });
  }

  private loadCount = 0;
  private checkLoaded(): void {
    this.loadCount++;
    if (this.loadCount >= 2) {
      this.loading.set(false);
    }
  }

  private toPodioItem(r: RankingItem) {
    return {
      pos: r.posicion,
      nombre: r.nombre_empresa,
      iniciales: r.nombre_empresa
        .split(' ')
        .filter(Boolean)
        .map((p) => p[0])
        .join('')
        .substring(0, 2)
        .toUpperCase(),
      // El podio muestra la utilidad acumulada (criterio del ranking).
      metric: this.guaraniPipe.transform(r.utilidad_acumulada),
    };
  }
}

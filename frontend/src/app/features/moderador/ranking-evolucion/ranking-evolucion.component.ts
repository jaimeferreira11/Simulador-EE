import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  LucideAngularModule,
  TrendingUp,
  TrendingDown,
  Minus,
  Download,
  LineChart,
} from 'lucide-angular';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';
import { CompetenciaApiService } from '../../../core/services/competencia-api.service';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
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

export type ModDeptTab = 'general' | 'gestion' | 'marketing' | 'finanzas';

interface ModDeptTabDef {
  key: ModDeptTab;
  label: string;
  metricLabel: string;
  sortFn: (a: RankingItem, b: RankingItem) => number;
  formatFn: (r: RankingItem) => string;
}

@Component({
  selector: 'app-ranking-evolucion',
  standalone: true,
  imports: [
    RouterLink,
    LucideAngularModule,
    BaseChartDirective,
    GuaraniCortoPipe,
    BotBadgeComponent,
  ],
  templateUrl: './ranking-evolucion.component.html',
})
export class RankingEvolucionComponent implements OnInit {
  readonly icons = { TrendingUp, TrendingDown, Minus, Download, LineChart };
  private guaraniPipe = new GuaraniCortoPipe();

  deptActual = signal<ModDeptTab>('general');

  readonly deptTabs: ModDeptTabDef[] = [
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

  deptRanking = computed(() => {
    const tab = this.deptTabs.find((t) => t.key === this.deptActual()) ?? this.deptTabs[0];
    const sorted = [...this.ranking()].sort(tab.sortFn);
    const map = this.equipoMap();
    return sorted.map((r, idx) => {
      const eq = map.get(r.equipo_id);
      return {
        pos: idx + 1,
        equipoId: r.equipo_id,
        nombre: r.nombre_empresa,
        color: r.codigo_color || CHART_COLORS[idx % CHART_COLORS.length],
        iniciales: r.nombre_empresa
          .split(' ')
          .filter(Boolean)
          .map((p) => p[0])
          .join('')
          .substring(0, 2)
          .toUpperCase(),
        metricValue: tab.formatFn(r),
        tipo: eq?.tipo,
        dificultad: eq?.dificultad,
        personalidad: eq?.personalidad,
      };
    });
  });

  activeDeptTab = computed(
    () => this.deptTabs.find((t) => t.key === this.deptActual()) ?? this.deptTabs[0],
  );

  private route = inject(ActivatedRoute);
  private competenciaApi = inject(CompetenciaApiService);
  private competenciaStore = inject(CompetenciaStore);
  private resultadoApi = inject(ResultadoApiService);

  loading = signal(true);
  competenciaNombre = signal('');
  numTrimestres = signal(4);
  ranking = signal<RankingItem[]>([]);
  evolucion = signal<EvolucionEquipo[]>([]);
  trimestresProcessados = signal(0);
  equipos = signal<Equipo[]>([]);

  /** Mapa equipoId → Equipo para mostrar metadatos (BOT) en el ranking. */
  equipoMap = computed<Map<number, Equipo>>(() => new Map(this.equipos().map((e) => [e.id, e])));

  equipoById(id: number): Equipo | undefined {
    return this.equipoMap().get(id);
  }

  trimestresArray = computed(() => Array.from({ length: this.numTrimestres() }, (_, i) => i + 1));

  nombreEquipos = computed(() =>
    this.ranking()
      .map((r) => r.nombre_empresa)
      .join(' | '),
  );

  tieneEvolucion = computed(() => this.evolucion().some((e) => e.serie_pip.length > 0));

  rows = computed(() => {
    const evo = this.evolucion();
    const rank = this.ranking();
    const map = this.equipoMap();
    return rank.map((r, idx) => {
      const evoItem = evo.find((e) => e.equipo_id === r.equipo_id);
      const pipPorQ: (number | null)[] = [];
      for (let q = 1; q <= this.numTrimestres(); q++) {
        const punto = evoItem?.serie_pip.find((s) => s.trimestre === q);
        pipPorQ.push(punto?.pip ?? null);
      }

      // Tendencia: comparar último Q con el anterior (o PIP base 100 si solo hay 1 Q)
      const processed = this.trimestresProcessados();
      let tendencia: 'up' | 'down' | 'neutral' = 'neutral';
      if (processed >= 1) {
        const current = pipPorQ[processed - 1];
        const prev = processed >= 2 ? pipPorQ[processed - 2] : 100;
        if (current != null && prev != null) {
          tendencia = current > prev ? 'up' : current < prev ? 'down' : 'neutral';
        }
      }

      const eq = map.get(r.equipo_id);
      return {
        equipoId: r.equipo_id,
        posicion: r.posicion,
        nombre: r.nombre_empresa,
        color: r.codigo_color || CHART_COLORS[idx % CHART_COLORS.length],
        iniciales: r.nombre_empresa
          .split(' ')
          .filter(Boolean)
          .map((w) => w[0])
          .join('')
          .substring(0, 2)
          .toUpperCase(),
        share: r.share_actual,
        caja: r.caja_actual,
        pipPorQ,
        pipTotal: r.pip_acumulado,
        tendencia,
        tipo: eq?.tipo,
        dificultad: eq?.dificultad,
        personalidad: eq?.personalidad,
      };
    });
  });

  mejorPip = computed(() => {
    const r = this.ranking();
    if (!r.length) return null;
    const best = r.reduce((a, b) => (a.pip_acumulado > b.pip_acumulado ? a : b));
    return { valor: best.pip_acumulado, nombre: best.nombre_empresa };
  });

  promedioPip = computed(() => {
    const r = this.ranking();
    if (!r.length) return null;
    const avg = r.reduce((sum, x) => sum + x.pip_acumulado, 0) / r.length;
    return avg;
  });

  peorPip = computed(() => {
    const r = this.ranking();
    if (!r.length) return null;
    const worst = r.reduce((a, b) => (a.pip_acumulado < b.pip_acumulado ? a : b));
    return { valor: worst.pip_acumulado, nombre: worst.nombre_empresa };
  });

  chartConfig = computed<ChartConfiguration<'line'>>(() => {
    const evo = this.evolucion();
    const labels = this.trimestresArray().map((n) => `Q${n}`);
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
          pointRadius: 5,
          pointHoverRadius: 7,
          borderWidth: 2,
        })),
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'bottom',
            labels: { font: { family: 'Inter', size: 12 }, usePointStyle: true, padding: 20 },
          },
          tooltip: {
            callbacks: {
              label: (ctx) => `${ctx.dataset.label}: ${ctx.parsed.y?.toFixed(2)} PIP`,
            },
          },
        },
        scales: {
          y: { title: { display: true, text: 'PIP Acumulado' }, beginAtZero: true },
          x: { title: { display: true, text: 'Trimestre' } },
        },
      },
    };
  });

  ngOnInit(): void {
    const competenciaId =
      Number(this.route.snapshot.queryParamMap.get('competencia')) ||
      this.competenciaStore.competenciaActiva()?.id;
    if (!competenciaId) {
      this.loading.set(false);
      return;
    }
    forkJoin({
      competencia: this.competenciaApi.getById(competenciaId),
      ranking: this.resultadoApi.getRanking(competenciaId),
      evolucion: this.resultadoApi.getEvolucion(competenciaId),
    }).subscribe({
      next: ({ competencia, ranking, evolucion }) => {
        this.competenciaNombre.set(competencia.nombre);
        this.competenciaStore.competenciaActiva.set(competencia);
        this.numTrimestres.set(competencia.num_trimestres);
        this.ranking.set(ranking);
        this.evolucion.set(evolucion);
        this.equipos.set(competencia.equipos ?? []);

        const processed =
          competencia.trimestres?.filter((t: any) => t.estado === 'PROCESADO').length ?? 0;
        this.trimestresProcessados.set(processed);

        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  exportar(): void {
    const compId = this.competenciaStore.competenciaActiva()?.id;
    if (!compId) return;
    this.resultadoApi.exportExcel(compId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `ranking_${this.competenciaNombre()}.xlsx`;
        a.click();
        URL.revokeObjectURL(url);
      },
    });
  }
}

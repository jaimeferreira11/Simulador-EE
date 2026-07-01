import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { LucideAngularModule, Check, Eye, Bot, TrendingUp } from 'lucide-angular';
import { JugadorStore } from '../../../core/stores/jugador.store';
import { ResultadoApiService } from '../../../core/services/resultado-api.service';
import { RankingItem, ResultadoCalculo } from '../../../core/models/resultado.model';
import { Trimestre } from '../../../core/models/trimestre.model';
import { GuaraniCortoPipe } from '../../../core/pipes/guarani-corto.pipe';

@Component({
  selector: 'app-resultados',
  standalone: true,
  imports: [LucideAngularModule, GuaraniCortoPipe],
  templateUrl: './resultados.component.html',
})
export class ResultadosComponent implements OnInit {
  readonly icons = { Check, Eye, Bot, TrendingUp };

  private jugadorStore = inject(JugadorStore);
  private resultadoApi = inject(ResultadoApiService);
  private route = inject(ActivatedRoute);

  loading = signal(true);
  ranking = signal<RankingItem[]>([]);
  resultado = signal<ResultadoCalculo | null>(null);
  trimestreSeleccionado = signal<number | null>(null);

  competencia = this.jugadorStore.competencia;
  equipo = this.jugadorStore.equipo;
  nombreEquipo = this.jugadorStore.nombreEquipo;

  // All processed trimestres for the tabs
  trimestresProcessados = computed(() => {
    return this.jugadorStore.trimestres()
      .filter(t => t.estado === 'PROCESADO')
      .map(t => t.numero);
  });

  trimestreNumero = computed(() => this.trimestreSeleccionado() ?? 0);

  // Stats from our resultado
  stats = computed(() => {
    const r = this.resultado();
    const rank = this.ranking().find(rk => rk.equipo_id === this.equipo()?.id);
    if (!r) return null;
    return {
      ingresos: r.ingresos,
      costos: r.costos_operativos_total,
      utilidadNeta: r.utilidad_neta,
      marketShare: r.share,
      posicion: rank?.posicion ?? 0,
    };
  });

  // Comparison table from ranking
  comparacion = computed(() => {
    const eq = this.equipo();
    return this.ranking().map(r => ({
      pos: r.posicion,
      nombre: r.nombre_empresa,
      iniciales: r.nombre_empresa.split(' ').filter(Boolean).map(p => p[0]).join('').substring(0, 2).toUpperCase(),
      ventas: r.utilidad_acumulada, // ranking doesn't have ventas, using utilidad
      marketShare: r.share_actual,
      utilidad: r.utilidad_acumulada,
      pip: r.pip_acumulado,
      highlight: eq ? r.equipo_id === eq.id : false,
    }));
  });

  coaching = signal<string | null>(null);
  coachingLoading = signal(false);

  ngOnInit(): void {
    this.initData();
  }

  private async initData(): Promise<void> {
    await this.jugadorStore.init();

    // Check query param for trimestre
    const qParam = this.route.snapshot.queryParamMap.get('trimestre');
    const procesados = this.trimestresProcessados();

    if (qParam) {
      const num = parseInt(qParam.replace('Q', ''), 10);
      if (procesados.includes(num)) {
        this.trimestreSeleccionado.set(num);
      } else if (procesados.length) {
        this.trimestreSeleccionado.set(procesados[procesados.length - 1]);
      }
    } else if (procesados.length) {
      this.trimestreSeleccionado.set(procesados[procesados.length - 1]);
    }

    this.loadTrimestreData();
  }

  selectTrimestre(num: number): void {
    this.trimestreSeleccionado.set(num);
    this.loadTrimestreData();
  }

  private loadTrimestreData(): void {
    const comp = this.competencia();
    const eq = this.equipo();
    const num = this.trimestreSeleccionado();
    if (!comp || !num) {
      this.loading.set(false);
      return;
    }

    // Find trimestre ID by numero
    const trim = this.jugadorStore.trimestres().find(t => t.numero === num);
    if (!trim) {
      this.loading.set(false);
      return;
    }

    this.loading.set(true);

    // Load ranking for this trimestre
    this.resultadoApi.getRanking(comp.id, trim.id).subscribe({
      next: (items) => {
        this.ranking.set(items);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });

    // Load resultado for our equipo
    if (eq) {
      this.resultadoApi.getResultado(eq.id, trim.id).subscribe({
        next: (r) => this.resultado.set(r),
        error: () => this.resultado.set(null),
      });

      // Load coaching
      this.coaching.set(null);
      this.coachingLoading.set(true);
      this.resultadoApi.getCoaching(eq.id, trim.id).subscribe({
        next: (c) => { this.coaching.set(c.texto); this.coachingLoading.set(false); },
        error: () => { this.coaching.set(null); this.coachingLoading.set(false); },
      });
    }
  }
}

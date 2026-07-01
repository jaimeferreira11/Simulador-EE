import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { CompetenciaApiService } from '../../../core/services/competencia-api.service';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { ResultadoApiService } from '../../../core/services/resultado-api.service';
import { CompetenciaDetalle } from '../../../core/models/competencia.model';
import { Equipo } from '../../../core/models/equipo.model';
import { Trimestre } from '../../../core/models/trimestre.model';
import { ResultadoCalculo, SnapshotEstado } from '../../../core/models/resultado.model';
import { GuaraniCortoPipe } from '../../../core/pipes/guarani-corto.pipe';

interface QData {
  trimestre: Trimestre;
  resultado: ResultadoCalculo | null;
  snapshot: SnapshotEstado | null;
}

interface ConceptoRow {
  label: string;
  values: (number | null)[];
  variacion: number | null;
  format: 'guarani' | 'percent' | 'decimal' | 'number';
  highlight: boolean;
}

@Component({
  selector: 'app-resultados-equipo-mod',
  standalone: true,
  imports: [GuaraniCortoPipe],
  templateUrl: './resultados-equipo.component.html',
})
export class ResultadosEquipoModComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private competenciaApi = inject(CompetenciaApiService);
  private competenciaStore = inject(CompetenciaStore);
  private resultadoApi = inject(ResultadoApiService);

  loading = signal(true);
  loadingData = signal(false);
  competencia = signal<CompetenciaDetalle | null>(null);
  equipos = signal<Equipo[]>([]);
  equipoSeleccionado = signal<Equipo | null>(null);
  trimestresProcessed = signal<Trimestre[]>([]);
  qData = signal<QData[]>([]);

  conceptos = computed<ConceptoRow[]>(() => {
    const data = this.qData();
    if (!data.length) return [];

    const buildRow = (
      label: string,
      extractor: (d: QData) => number | null,
      format: ConceptoRow['format'],
      highlight = false
    ): ConceptoRow => {
      const values = data.map(extractor);
      let variacion: number | null = null;
      if (values.length >= 2) {
        const last = values[values.length - 1];
        const prev = values[values.length - 2];
        if (last != null && prev != null && prev !== 0) {
          variacion = ((last - prev) / Math.abs(prev)) * 100;
        }
      }
      return { label, values, variacion, format, highlight };
    };

    return [
      buildRow('Ingresos por Ventas', d => d.resultado?.ingresos ?? null, 'guarani'),
      buildRow('Costos de Producción', d => {
        const r = d.resultado;
        if (!r) return null;
        return r.costo_mp_total + r.costo_laboral;
      }, 'guarani'),
      buildRow('Costos Operativos', d => d.resultado?.costos_operativos_total ?? null, 'guarani'),
      buildRow('Utilidad Neta', d => d.resultado?.utilidad_neta ?? null, 'guarani', true),
      buildRow('Efectivo', d => d.snapshot?.caja ?? null, 'guarani'),
      buildRow('Deuda', d => d.snapshot?.deuda ?? null, 'guarani'),
      buildRow('Brand Equity', d => d.snapshot?.brand_equity ?? null, 'decimal'),
      buildRow('Market Share', d => d.resultado?.share ?? null, 'percent'),
      buildRow('PIP Score', d => d.resultado?.pip_trimestre ?? null, 'decimal', true),
    ];
  });

  ngOnInit(): void {
    const competenciaId = Number(this.route.snapshot.queryParamMap.get('competencia'))
      || this.competenciaStore.competenciaActiva()?.id;
    if (!competenciaId) {
      this.loading.set(false);
      return;
    }
    this.competenciaApi.getById(competenciaId).subscribe({
      next: (c) => {
        this.competencia.set(c);
        this.competenciaStore.competenciaActiva.set(c);
        this.equipos.set(c.equipos);
        const processed = c.trimestres.filter(t => t.estado === 'PROCESADO');
        this.trimestresProcessed.set(processed);
        if (c.equipos.length && processed.length) {
          this.selectEquipo(c.equipos[0]);
        } else {
          this.loading.set(false);
        }
      },
      error: () => this.loading.set(false),
    });
  }

  selectEquipo(equipo: Equipo): void {
    this.equipoSeleccionado.set(equipo);
    this.loadEquipoData(equipo);
  }

  private loadEquipoData(equipo: Equipo): void {
    this.loadingData.set(true);
    const processed = this.trimestresProcessed();
    const calls = processed.map(t =>
      forkJoin({
        trimestre: of(t),
        resultado: this.resultadoApi.getResultado(equipo.id, t.id).pipe(catchError(() => of(null))),
        snapshot: this.resultadoApi.getSnapshot(equipo.id, t.id, 'CIERRE').pipe(catchError(() => of(null))),
      })
    );

    if (!calls.length) {
      this.qData.set([]);
      this.loading.set(false);
      this.loadingData.set(false);
      return;
    }

    forkJoin(calls).subscribe({
      next: (results) => {
        this.qData.set(results as QData[]);
        this.loading.set(false);
        this.loadingData.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadingData.set(false);
      },
    });
  }

  formatValue(value: number | null, format: ConceptoRow['format']): string {
    if (value == null) return '—';
    switch (format) {
      case 'guarani':
        return '₲ ' + Math.round(value).toLocaleString('es-PY');
      case 'percent':
        return (value * 100).toFixed(1) + '%';
      case 'decimal':
        return value.toFixed(2);
      case 'number':
        return Math.round(value).toLocaleString('es-PY');
    }
  }

  formatVariacion(v: number | null): string {
    if (v == null) return '—';
    const sign = v >= 0 ? '+' : '';
    return sign + v.toFixed(1) + '%';
  }
}

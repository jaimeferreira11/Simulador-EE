import { Component, input, output, computed } from '@angular/core';
import { LucideAngularModule, ArrowLeft, CheckCircle, TriangleAlert, Info } from 'lucide-angular';
import {
  ContextoDecision, ProyeccionFinanciera,
  CAMPOS_POR_AREA, AREA_COLORS, AREA_LABELS, AreaDecisionV2,
} from '../../../../core/models/contexto-decision.model';

interface FilaResumen {
  area: AreaDecisionV2;
  areaLabel: string;
  campo: string;
  field: string;
  valorActual: number | null;
  delta: string;
  deltaPositive: boolean;
  deltaNeutral: boolean;
  esNuevo: boolean;
  unidad: string;
}

@Component({
  selector: 'app-revision',
  standalone: true,
  imports: [LucideAngularModule],
  templateUrl: './revision.component.html',
})
export class RevisionComponent {
  readonly icons = { ArrowLeft, CheckCircle, TriangleAlert, Info };
  contexto = input.required<ContextoDecision>();
  formValues = input.required<Record<string, number | null>>();
  proyeccion = input<ProyeccionFinanciera | null>(null);
  soloLectura = input(false);
  saving = input(false);

  editar = output<void>();
  enviar = output<void>();

  readonly areaColors = AREA_COLORS;
  readonly areaLabels = AREA_LABELS;

  esCapitan = computed(() => this.contexto().permisos.es_capitan);

  trimestreAnterior = computed(() => {
    const num = this.contexto().trimestre.numero;
    return num > 1 ? num - 1 : null;
  });

  filas = computed<FilaResumen[]>(() => {
    const vals = this.formValues();
    const ant = this.contexto().decision_anterior;
    const result: FilaResumen[] = [];

    const areas: AreaDecisionV2[] = ['COMERCIAL', 'OPERACIONES', 'TALENTO_HUMANO', 'FINANZAS'];
    for (const area of areas) {
      for (const campo of CAMPOS_POR_AREA[area]) {
        const actual = vals[campo.field] ?? null;
        if (actual == null || actual === 0) continue;

        const anterior = ant ? (ant as any)[campo.field] ?? null : null;
        let delta = '';
        let deltaPositive = true;
        let deltaNeutral = false;
        let esNuevo = false;

        if (anterior == null) {
          esNuevo = true;
        } else if (anterior === 0) {
          deltaNeutral = true;
          delta = '=';
        } else {
          const diff = actual - anterior;
          if (diff === 0) {
            deltaNeutral = true;
            delta = '=';
          } else {
            const pct = ((diff / Math.abs(anterior)) * 100).toFixed(0);
            delta = (diff > 0 ? '+' : '') + pct + '%';
            deltaPositive = diff >= 0;
          }
        }

        result.push({
          area, areaLabel: AREA_LABELS[area], campo: campo.label,
          field: campo.field, valorActual: actual, delta, deltaPositive,
          deltaNeutral, esNuevo, unidad: campo.unidad,
        });
      }
    }
    return result;
  });

  proyeccionRows = computed(() => {
    const proy = this.proyeccion();
    if (!proy) return [];

    const idActual = this.contexto().snapshot_inicio.id_acumulado;
    const idPromedio = this.contexto().mercado.id_acum_promedio;
    const idDelta = idPromedio > 0 ? ((idActual - idPromedio) / idPromedio * 100).toFixed(0) : '0';

    return [
      {
        label: 'Caja proyectada',
        value: 'Gs ' + (proy.caja_proyectada / 1_000_000).toFixed(1) + 'M',
        status: proy.semaforo_caja === 'verde' ? 'OK' : proy.semaforo_caja === 'amarillo' ? 'AJUSTADO' : 'DEFICIT',
        statusColor: proy.semaforo_caja === 'verde' ? 'green' : proy.semaforo_caja === 'amarillo' ? 'amber' : 'red',
      },
      {
        label: 'Utilizacion planta',
        value: (proy.utilizacion_planta * 100).toFixed(1) + '%',
        status: proy.utilizacion_planta > 0.85 ? 'ALTO' : proy.utilizacion_planta > 0.6 ? 'OK' : 'BAJO',
        statusColor: proy.utilizacion_planta > 0.85 ? 'amber' : proy.utilizacion_planta > 0.6 ? 'green' : 'red',
      },
      {
        label: 'I+D vs promedio',
        value: (Number(idDelta) >= 0 ? '+' : '') + idDelta + '%',
        status: Number(idDelta) > 0 ? 'ARRIBA' : Number(idDelta) === 0 ? 'IGUAL' : 'ABAJO',
        statusColor: Number(idDelta) > 0 ? 'blue' : Number(idDelta) === 0 ? 'gray' : 'red',
      },
    ];
  });

  formatValue(fila: FilaResumen): string {
    if (fila.valorActual == null) return '—';
    if (fila.unidad === 'guarani') return 'Gs ' + fila.valorActual.toLocaleString('es-PY');
    if (fila.unidad === 'porcentaje') return fila.valorActual + '%';
    if (fila.unidad === 'personas') return '+' + fila.valorActual;
    return fila.valorActual.toLocaleString('es-PY') + ' u';
  }

  statusBgColor(color: string): string {
    switch (color) {
      case 'green': return '#DCFCE7';
      case 'amber': return '#FEF3C7';
      case 'red': return '#FEE2E2';
      case 'blue': return '#DBEAFE';
      default: return '#F3F4F6';
    }
  }

  statusTextColor(color: string): string {
    switch (color) {
      case 'green': return '#15803D';
      case 'amber': return '#B45309';
      case 'red': return '#DC2626';
      case 'blue': return '#1D4ED8';
      default: return '#6B7280';
    }
  }
}

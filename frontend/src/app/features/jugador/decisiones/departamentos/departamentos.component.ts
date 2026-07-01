import { Component, input, output, computed, signal } from '@angular/core';
import {
  LucideAngularModule,
  Clock,
  CheckCircle,
  Lock,
  TriangleAlert,
  DollarSign,
  ArrowRight,
  CupSoda,
} from 'lucide-angular';
import { DecisionInput } from '../../../../core/models/decision.model';
import {
  ContextoDecision,
  ProyeccionFinanciera,
  AreaDecisionV2,
  CAMPOS_POR_AREA,
  AREA_COLORS,
  AREA_LABELS,
} from '../../../../core/models/contexto-decision.model';
import { CampoDecisionComponent } from '../shared/campo-decision.component';
import { EventoCardComponent } from '../shared/evento-card.component';
import { GuaraniPipe } from '../../../../core/pipes/guarani.pipe';

const AREAS: AreaDecisionV2[] = ['COMERCIAL', 'OPERACIONES', 'TALENTO_HUMANO', 'FINANZAS'];

@Component({
  selector: 'app-departamentos',
  standalone: true,
  imports: [LucideAngularModule, CampoDecisionComponent, EventoCardComponent, GuaraniPipe],
  templateUrl: './departamentos.component.html',
})
export class DepartamentosComponent {
  contexto = input.required<ContextoDecision>();
  formValues = input.required<Record<string, number | null>>();
  proyeccion = input<ProyeccionFinanciera | null>(null);
  soloLectura = input(false);
  saving = input(false);
  equipoId = input<number>(0);

  fieldChange = output<{ field: string; value: number | null }>();
  guardar = output<void>();
  revisar = output<void>();

  readonly icons = { Clock, CheckCircle, Lock, TriangleAlert, DollarSign, ArrowRight, CupSoda };
  activeTab = signal<AreaDecisionV2>('COMERCIAL');

  // Días operativos por trimestre para derivar la "capacidad diaria" mostrada al jugador.
  // Solo es una aclaración visual: el motor modela la capacidad por trimestre (≈ 3 meses / 90 días).
  private readonly DIAS_OPERATIVOS_TRIMESTRE = 90;

  readonly areas = AREAS;
  readonly areaLabels = AREA_LABELS;
  readonly areaColors = AREA_COLORS;

  camposTab = computed(() => CAMPOS_POR_AREA[this.activeTab()]);

  permisos = computed(() => this.contexto().permisos);
  esCapitan = computed(() => this.permisos().es_capitan);

  canEditArea = computed(() => {
    return (area: AreaDecisionV2) => {
      if (this.soloLectura()) return false;
      if (this.esCapitan()) return true;
      return this.permisos().area_asignada === area;
    };
  });

  canEditField = computed(() => {
    return (field: keyof DecisionInput) => {
      if (this.soloLectura()) return false;
      return this.permisos().campos_editables.includes(field);
    };
  });

  eventosParaTab = computed(() => {
    const tab = this.activeTab();
    return this.contexto().eventos_activos.filter((e) => e.areas_impactadas.includes(tab));
  });

  // Derivado del catálogo (no hardcodeado): sólo cuenta los campos visibles para el jugador.
  totalCampos = AREAS.reduce((sum, area) => sum + CAMPOS_POR_AREA[area].length, 0);
  camposCompletados = computed(() => {
    const vals = this.formValues();
    return AREAS.reduce(
      (sum, area) =>
        sum +
        CAMPOS_POR_AREA[area].filter((c) => vals[c.field] != null && vals[c.field] !== 0).length,
      0,
    );
  });

  todoCompleto = computed(() => this.camposCompletados() === this.totalCampos);

  camposCompletadosArea = computed(() => {
    const vals = this.formValues();
    return (area: AreaDecisionV2) => {
      const campos = CAMPOS_POR_AREA[area];
      return campos.filter((c) => vals[c.field] != null && vals[c.field] !== 0).length;
    };
  });

  camposTotalArea(area: AreaDecisionV2): number {
    return CAMPOS_POR_AREA[area].length;
  }

  areaCompleta = computed(() => {
    const completados = this.camposCompletadosArea();
    return (area: AreaDecisionV2) => completados(area) === CAMPOS_POR_AREA[area].length;
  });

  progresoEquipo = computed(() => {
    return AREAS.map((area) => ({
      area: AREA_LABELS[area],
      completos: this.camposCompletadosArea()(area),
      total: CAMPOS_POR_AREA[area].length,
    }));
  });

  cierreEn = computed(() => {
    const t = this.contexto().trimestre;
    if (!t.cierre_at) return 'Sin fecha de cierre';
    const diff = new Date(t.cierre_at).getTime() - Date.now();
    if (isNaN(diff)) return 'Sin fecha de cierre';
    if (diff <= 0) return 'Vencido';
    const days = Math.floor(diff / 86400000);
    const hours = Math.floor((diff % 86400000) / 3600000);
    return `${days} días, ${hours} horas`;
  });

  /** Costo unitario de materia prima (Gs/unidad) provisto por el backend. */
  costoUnitarioMp = computed(() => this.contexto().costo_unitario_mp ?? 0);

  /** Producto concreto del rubro y su BOM. null si el rubro no tiene producto. */
  producto = computed(() => this.contexto().producto ?? null);

  /** Unidades de producción planificada según el valor vivo del formulario. */
  produccionPlanificada = computed(() => this.formValues()['produccion_planificada'] ?? 0);

  /** Costo total estimado de materia prima = producción × costo unitario, reactivo. */
  costoTotalMp = computed(() => this.produccionPlanificada() * this.costoUnitarioMp());

  selectTab(area: AreaDecisionV2): void {
    this.activeTab.set(area);
  }

  getFieldValue(field: string): number | null {
    return this.formValues()[field] ?? null;
  }

  getAnteriorValue(field: string): number | null {
    const ant = this.contexto().decision_anterior;
    if (!ant) return null;
    return (ant as any)[field] ?? null;
  }

  onFieldChange(field: string, value: number | null): void {
    this.fieldChange.emit({ field, value });
  }

  get contextTitle(): string {
    switch (this.activeTab()) {
      case 'COMERCIAL':
        return 'TU POSICION EN EL MERCADO';
      case 'OPERACIONES':
        return 'DATOS DE PLANTA';
      case 'TALENTO_HUMANO':
        return 'TU EQUIPO HUMANO';
      case 'FINANZAS':
        return 'SITUACION FINANCIERA';
    }
  }

  get contextData(): { label: string; value: string }[] {
    const snap = this.contexto().snapshot_inicio;
    const mercado = this.contexto().mercado;
    const limites = this.contexto().limites;

    switch (this.activeTab()) {
      case 'COMERCIAL':
        return [
          {
            label: 'Market share',
            value:
              (
                (this.contexto().ranking_anterior.find((r) => r.equipo_id === this.equipoId())
                  ?.share ?? 0) * 100
              ).toFixed(0) +
              '% (#' +
              (this.contexto().ranking_anterior.find((r) => r.equipo_id === this.equipoId())
                ?.posicion ?? 0) +
              ')',
          },
          { label: 'Brand Equity', value: Math.round(snap.brand_equity * 100) + '/100' },
          { label: 'Calidad percibida', value: snap.calidad_percibida.toFixed(2) },
        ];
      case 'OPERACIONES':
        return [
          {
            label: 'Capacidad planta',
            value: snap.capacidad.toLocaleString('es-PY') + ' uds/trim',
          },
          {
            label: 'Capacidad diaria',
            value:
              '≈ ' +
              Math.round(snap.capacidad / this.DIAS_OPERATIVOS_TRIMESTRE).toLocaleString('es-PY') +
              ' uds/día',
          },
          { label: 'Inventario', value: snap.inventario + ' uds' },
          { label: 'I+D acumulado', value: 'Gs ' + snap.id_acumulado.toLocaleString('es-PY') },
          {
            label: 'I+D prom. mercado',
            value: 'Gs ' + mercado.id_acum_promedio.toLocaleString('es-PY'),
          },
        ];
      case 'TALENTO_HUMANO':
        return [
          { label: 'Headcount actual', value: snap.headcount + ' personas' },
          { label: 'Salario promedio', value: 'Gs ' + snap.salario.toLocaleString('es-PY') },
          {
            label: 'SMV vigente',
            value: 'Gs ' + limites.salario_minimo_legal.toLocaleString('es-PY'),
          },
          {
            label: 'Productividad',
            value: (snap.capacidad / snap.headcount).toFixed(1) + ' uds/emp',
          },
        ];
      case 'FINANZAS':
        return [
          { label: 'Caja actual', value: 'Gs ' + snap.caja.toLocaleString('es-PY') },
          { label: 'Deuda total', value: 'Gs ' + snap.deuda.toLocaleString('es-PY') },
          { label: 'Patrimonio neto', value: 'Gs ' + snap.patrimonio_neto.toLocaleString('es-PY') },
          {
            label: 'Límite préstamo',
            value: 'Gs ' + limites.prestamo_maximo.toLocaleString('es-PY'),
          },
        ];
    }
  }

  get pesosData(): { label: string; value: number; color: string }[] {
    const pesos = this.contexto().pesos_competitividad;
    return [
      { label: 'Precio', value: pesos.precio * 100, color: '#DC2626' },
      { label: 'Marketing', value: pesos.marketing * 100, color: '#F47920' },
      { label: 'Calidad (I+D)', value: pesos.calidad * 100, color: '#3B82F6' },
      { label: 'Marca (BE)', value: pesos.marca * 100, color: '#1A1A1A' },
    ];
  }

  get pesosWarning(): string | null {
    const eventos = this.contexto().eventos_activos;
    const override = eventos.find((e) => e.override_pesos != null);
    if (!override) return null;
    return `Crisis: peso precio sube de 40% a 50%`;
  }

  get cajaInicio(): number {
    return this.contexto().snapshot_inicio.caja;
  }
}

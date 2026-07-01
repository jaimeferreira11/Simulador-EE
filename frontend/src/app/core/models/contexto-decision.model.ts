import { DecisionInput } from './decision.model';

// ── Snapshot del estado de la empresa al INICIO del trimestre ──
export interface SnapshotInicio {
  caja: number;
  deuda: number;
  patrimonio_neto: number;
  capacidad: number;
  headcount: number;
  salario: number;
  inventario: number;
  brand_equity: number;
  calidad_percibida: number;
  id_acumulado: number;
  pip: number;
}

// ── Datos de mercado agregados ──
export interface DatosMercado {
  demanda_total_estimada: number;
  precio_promedio: number;
  id_acum_promedio: number;
  marketing_promedio: number;
  inflacion_acumulada: number;
}

// ── Ranking del trimestre anterior ──
export interface RankingAnteriorItem {
  posicion: number;
  equipo_id: number;
  nombre_empresa: string;
  pip: number;
  share: number;
}

// ── Evento activo con impacto narrativo ──
export interface EventoActivo {
  nombre: string;
  severidad: 'GRAVE' | 'MODERADO' | 'POSITIVO';
  tipo_efecto: string;
  magnitud: number;
  duracion_restante: number;
  descripcion: string;
  areas_impactadas: AreaDecisionV2[];
  override_pesos: PesosCompetitividad | null;
}

export type AreaDecisionV2 = 'COMERCIAL' | 'OPERACIONES' | 'TALENTO_HUMANO' | 'FINANZAS';

export interface PesosCompetitividad {
  precio: number;
  marketing: number;
  calidad: number;
  marca: number;
}

// ── Limites calculados ──
export interface Limites {
  prestamo_maximo: number;
  dividendo_maximo: number;
  capacidad_maxima_produccion: number;
  salario_minimo_legal: number;
  puede_pedir_prestamo: boolean;
  razon_bloqueo_prestamo: string | null;
}

// ── Permisos del jugador actual ──
export interface Permisos {
  es_capitan: boolean;
  area_asignada: AreaDecisionV2 | null;
  campos_editables: (keyof DecisionInput)[];
}

// ── Materia prima del BOM del producto del rubro ──
export interface MateriaPrimaRubro {
  nombre: string;
  costo_unitario: number;
}

// ── Producto concreto del rubro y su Bill of Materials (BOM) ──
// READ-ONLY: informacion narrativa para el panel de Operaciones.
export interface ProductoRubro {
  nombre: string;
  descripcion: string;
  unidad_medida: string;
  costo_base_unitario: number;
  materias_primas: MateriaPrimaRubro[];
}

// ── Contexto completo para la pantalla de decisiones ──
export interface ContextoDecision {
  trimestre: {
    numero: number;
    estado: string;
    cierre_at: string;
  };
  snapshot_inicio: SnapshotInicio;
  mercado: DatosMercado;
  ranking_anterior: RankingAnteriorItem[];
  eventos_activos: EventoActivo[];
  decision_anterior: DecisionInput | null;
  limites: Limites;
  permisos: Permisos;
  pesos_competitividad: PesosCompetitividad;
  // Costo unitario de materia prima (guaraníes/unidad). Top-level en el DTO backend.
  costo_unitario_mp: number;
  // Producto concreto del rubro y su BOM. null si el rubro no tiene producto asociado.
  producto: ProductoRubro | null;
}

// ── Proyeccion financiera (what-if) ──
export interface Advertencia {
  tipo: 'WARNING' | 'INFO';
  campo: string;
  mensaje: string;
}

export interface ProyeccionFinanciera {
  caja_proyectada: number;
  ingresos_estimados: number;
  costos_variables_est: number;
  costos_fijos_est: number;
  costo_laboral_est: number;
  intereses_est: number;
  inversion_total: number;
  utilizacion_planta: number;
  semaforo_caja: 'verde' | 'amarillo' | 'rojo';
  advertencias: Advertencia[];
}

// ── Metadata de campos por area ──
export interface CampoMeta {
  field: keyof DecisionInput;
  label: string;
  area: AreaDecisionV2;
  unidad: 'guarani' | 'unidades' | 'porcentaje' | 'personas';
  tooltip: string;
  helper?: string;
  placeholder: string;
}

export const CAMPOS_POR_AREA: Record<AreaDecisionV2, CampoMeta[]> = {
  COMERCIAL: [
    {
      field: 'precio_venta',
      label: 'Precio de Venta',
      area: 'COMERCIAL',
      unidad: 'guarani',
      tooltip:
        'El precio tiene peso de 40% en la competitividad. Un precio menor al promedio atrae mas demanda, pero reduce tu margen por unidad vendida.',
      helper: 'Precio prom. mercado: Gs 42,000',
      placeholder: '30.000 - 55.000',
    },
    {
      field: 'inversion_marketing',
      label: 'Inversion en Marketing',
      area: 'COMERCIAL',
      unidad: 'guarani',
      tooltip:
        'Marketing tiene peso de 30% en competitividad. Tiene retornos decrecientes: duplicar la inversion solo aumenta el efecto en ~41%.',
      placeholder: '0',
    },
  ],
  OPERACIONES: [
    {
      field: 'produccion_planificada',
      label: 'Produccion Planificada',
      area: 'OPERACIONES',
      unidad: 'unidades',
      tooltip: 'Producir 75-85% de capacidad es optimo. Menos = costo ocioso. Mas = horas extra.',
      placeholder: 'Segun capacidad',
    },
    {
      field: 'inversion_capacidad',
      label: 'Inversion en Capacidad',
      area: 'OPERACIONES',
      unidad: 'guarani',
      tooltip: 'Cada Gs invertido agrega ~2.5 unidades de capacidad. Efecto permanente.',
      placeholder: '0',
    },
    {
      field: 'inversion_id',
      label: 'Inversion en I+D',
      area: 'OPERACIONES',
      unidad: 'guarani',
      tooltip:
        'I+D se acumula pero decae 15%/trimestre si no se mantiene. Afecta calidad (20% de competitividad).',
      placeholder: '0',
    },
  ],
  TALENTO_HUMANO: [
    {
      field: 'contrataciones_netas',
      label: 'Contrataciones Netas',
      area: 'TALENTO_HUMANO',
      unidad: 'personas',
      tooltip:
        'Positivo = contratar, negativo = despedir. Cada contratacion cuesta Gs 500.000. Despido cuesta 2x salario.',
      placeholder: '0',
    },
    {
      field: 'aumento_salarial_pct',
      label: 'Aumento Salarial %',
      area: 'TALENTO_HUMANO',
      unidad: 'porcentaje',
      tooltip:
        'Salario debe ser >= SMV. Aumentos por debajo de inflacion generan insatisfaccion y rotacion.',
      placeholder: '0',
    },
    {
      field: 'inversion_capacitacion',
      label: 'Inversion en Capacitacion',
      area: 'TALENTO_HUMANO',
      unidad: 'guarani',
      tooltip:
        'Capacitacion mejora productividad con retorno diferido (2-3 trimestres). Efecto acumulativo.',
      placeholder: '0',
    },
  ],
  FINANZAS: [
    {
      field: 'prestamo_solicitado',
      label: 'Prestamo Solicitado',
      area: 'FINANZAS',
      unidad: 'guarani',
      tooltip:
        'Limite: 2x patrimonio neto. Bloqueado si ratio deuda/activo > 1.0 o 2 trimestres consecutivos con perdida.',
      placeholder: '0',
    },
    // Dividendos omitido del flujo del jugador (decisión de producto, feedback prueba interna).
    // El backend sigue recibiendo dividendos_pagar=0; el motor y el Golden File no se ven afectados.
  ],
};

export const AREA_COLORS: Record<AreaDecisionV2, { main: string; bg: string; border: string }> = {
  COMERCIAL: { main: '#F47920', bg: '#FEF0E4', border: '#F47920' },
  OPERACIONES: { main: '#3B82F6', bg: '#DBEAFE', border: '#3B82F6' },
  TALENTO_HUMANO: { main: '#16A34A', bg: '#DCFCE7', border: '#16A34A' },
  FINANZAS: { main: '#9333EA', bg: '#F3E8FF', border: '#9333EA' },
};

export const AREA_LABELS: Record<AreaDecisionV2, string> = {
  COMERCIAL: 'Comercial',
  OPERACIONES: 'Operaciones',
  TALENTO_HUMANO: 'Talento Humano',
  FINANZAS: 'Finanzas',
};

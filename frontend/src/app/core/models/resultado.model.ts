export interface ResultadoCalculo {
  id: number;
  equipo_id: number;
  trimestre_id: number;
  utilizacion_capacidad: number;
  factor_eficiencia: number;
  produccion_real: number;
  demanda_total_mercado: number;
  demanda_asignada: number;
  competitividad: number;
  share: number;
  ventas_unidades: number;
  ingresos: number;
  costo_mp_total: number;
  costo_laboral: number;
  costo_fijo: number;
  costo_marketing: number;
  costo_id: number;
  costo_capacitacion: number;
  costo_almacenamiento: number;
  depreciacion: number;
  intereses: number;
  costos_operativos_total: number;
  utilidad_operativa: number;
  utilidad_antes_impuestos: number;
  impuesto_ire: number;
  utilidad_neta: number;
  pip_trimestre: number;
  calculado_at: string;
}

export interface RankingItem {
  posicion: number;
  equipo_id: number;
  nombre_empresa: string;
  codigo_color: string;
  pip_acumulado: number;
  utilidad_acumulada: number;
  caja_actual: number;
  share_actual: number;
}

export interface SnapshotEstado {
  id: number;
  equipo_id: number;
  trimestre_id: number;
  momento: 'INICIO' | 'CIERRE';
  caja: number;
  capacidad: number;
  inventario: number;
  headcount: number;
  salario: number;
  brand_equity: number;
  calidad: number;
  deuda: number;
  patrimonio: number;
  valor_planta: number;
  id_acumulado: number;
}

export interface EvolucionEquipo {
  equipo_id: number;
  nombre_empresa: string;
  codigo_color: string;
  serie_pip: { trimestre: number; pip: number }[];
}

export interface CoachingTrimestre {
  id: number;
  trimestre_id: number;
  equipo_id: number;
  texto: string;
  created_at: string;
}

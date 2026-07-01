export type EstadoDecision = 'BORRADOR' | 'ENVIADA' | 'PROCESADA';

export interface Decision {
  id: number;
  equipo_id: number;
  trimestre_id: number;
  registrado_por_usuario_id: number;
  prestamo_solicitado: number;
  dividendos_pagar: number;
  produccion_planificada: number;
  inversion_capacidad: number;
  precio_venta: number;
  inversion_marketing: number;
  contrataciones_netas: number;
  aumento_salarial_pct: number;
  inversion_capacitacion: number;
  inversion_id: number;
  estado: EstadoDecision;
  submitted_at: string | null;
  updated_at: string;
}

export interface DecisionInput {
  prestamo_solicitado?: number;
  dividendos_pagar?: number;
  produccion_planificada?: number;
  inversion_capacidad?: number;
  precio_venta: number;
  inversion_marketing?: number;
  contrataciones_netas?: number;
  aumento_salarial_pct?: number;
  inversion_capacitacion?: number;
  inversion_id?: number;
}

export interface ValidacionDecision {
  es_valida: boolean;
  caja_proyectada_min: number;
  errores: { field: string; message: string }[];
}

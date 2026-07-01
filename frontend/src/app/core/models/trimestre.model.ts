export type EstadoTrimestre =
  | 'PENDIENTE'
  | 'ABIERTO_DECISIONES'
  | 'CERRADO_PROCESANDO'
  | 'PROCESADO'
  | 'ANULADO';

export interface Trimestre {
  id: number;
  competencia_id: number;
  numero: number;
  estado: EstadoTrimestre;
  apertura_at: string | null;
  cierre_at: string | null;
  procesado_at: string | null;
}

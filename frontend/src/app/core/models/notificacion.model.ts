export type SeveridadNotificacion = 'INFO' | 'IMPORTANTE' | 'URGENTE';

export interface Notificacion {
  id: number;
  tipo: string;
  titulo: string;
  descripcion: string | null;
  severidad: SeveridadNotificacion;
  leida: boolean;
  created_at: string;
  competencia_id: number | null;
  datos_extra: Record<string, unknown> | null;
}

export interface NotificacionPage {
  content: Notificacion[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

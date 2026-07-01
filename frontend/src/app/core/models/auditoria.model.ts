export interface AuditoriaEvento {
  id: number;
  competencia_id: number;
  usuario_id: number | null;
  tipo_accion: string;
  descripcion: string;
  datos_contexto: Record<string, any> | null;
  ip_origen: string | null;
  ocurrido_at: string;
}

export interface PagedAuditoriaEventos {
  content: AuditoriaEvento[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

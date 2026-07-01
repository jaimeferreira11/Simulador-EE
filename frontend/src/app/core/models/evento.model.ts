export type Severidad = 'LEVE' | 'MODERADO' | 'GRAVE' | 'POSITIVO';
export type TipoEfecto = 'COSTO_LOGISTICO' | 'COSTO_FIJO' | 'COSTO_MP' | 'DEMANDA_TOTAL' | 'TASA_INTERES' | 'TIPO_CAMBIO';

export interface EventoCatalogo {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string;
  severidad: Severidad;
  tipo_efecto: TipoEfecto;
  magnitud_default: number;
  duracion_q: number;
  requiere_anuncio_previo: boolean;
  activo: boolean;
  override_peso_precio: number | null;
  override_peso_marketing: number | null;
  override_peso_calidad: number | null;
  override_peso_marca: number | null;
}

export interface EventoCompetencia {
  id: number;
  competencia_id: number;
  trimestre_id: number;
  evento_catalogo_id: number;
  evento_catalogo: EventoCatalogo;
  disparado_at: string;
  justificacion: string;
}

export interface EventoDisparar {
  trimestre_id: number;
  evento_catalogo_id: number;
  justificacion: string;
}

export interface EventoAutomatico {
  id: number;
  regla_nombre: string;
  regla_descripcion: string;
  equipo_id: number;
  nombre_empresa: string;
  trimestre_origen: number;
  trimestre_efecto_inicio: number;
  trimestre_efecto_fin: number;
  efecto_tipo: string;
  efecto_valor: number;
}

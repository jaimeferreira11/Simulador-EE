import { BotDificultad, BotPersonalidad, BotTipo } from './bot.model';
import { Usuario } from './usuario.model';

export type EstadoEquipo = 'ACTIVO' | 'INTERVENIDO' | 'QUEBRADO' | 'ELIMINADO';

export interface Equipo {
  id: number;
  competencia_id: number;
  nombre_empresa: string;
  codigo_color: string;
  estado: EstadoEquipo;
  posicion_final: number | null;
  pip_final: number | null;
  tipo?: BotTipo;
  dificultad?: BotDificultad | null;
  personalidad?: BotPersonalidad | null;
}

export interface EquipoMiembro {
  id: number;
  equipo_id: number;
  usuario_id: number;
  usuario: Usuario;
  area_id: number | null;
  es_capitan: boolean;
  joined_at: string;
}

export interface EquipoDetalle extends Equipo {
  miembros: EquipoMiembro[];
}

export interface EquipoCreate {
  nombre_empresa: string;
  codigo_color: string;
  tipo?: BotTipo;
  dificultad?: BotDificultad | null;
}

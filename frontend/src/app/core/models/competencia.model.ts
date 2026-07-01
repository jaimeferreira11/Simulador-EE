import { Rubro, ParametroMacro, ParametroRubro } from './catalogo.model';
import { Equipo } from './equipo.model';
import { Trimestre } from './trimestre.model';

export type EstadoCompetencia =
  | 'BORRADOR'
  | 'ABIERTA_INSCRIPCION'
  | 'EN_CURSO'
  | 'PAUSADA'
  | 'PENDIENTE_FINALIZAR'
  | 'FINALIZADA'
  | 'ARCHIVADA';

export interface Entidad {
  id: number;
  nombre: string;
  tipo: 'UNIVERSIDAD' | 'COLEGIO' | 'EMPRESA' | 'ONG' | 'INSTITUTO' | 'OTRO';
  descripcion: string | null;
  contacto_nombre: string | null;
  contacto_email: string | null;
  activa: boolean;
  created_at: string;
}

export interface Competencia {
  id: number;
  codigo: string;
  nombre: string;
  rubro_id: number;
  parametro_macro_id: number;
  parametro_rubro_id: number;
  moderador_id: number;
  entidad_id: number | null;
  num_trimestres: number;
  num_equipos_max: number;
  max_integrantes_equipo: number | null;
  caja_inicial: number;
  capacidad_inicial: number;
  headcount_inicial: number;
  salario_inicial: number;
  inventario_inicial: number;
  valor_planta_inicial: number;
  escenario_id: number | null;
  ia_habilitada: boolean;
  estado: EstadoCompetencia;
  inicio_at: string | null;
  cierre_at: string | null;
  created_at: string;
}

export interface PagedCompetencias {
  content: Competencia[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

export interface CompetenciaDetalle extends Competencia {
  rubro: Rubro;
  parametro_macro: ParametroMacro;
  parametro_rubro: ParametroRubro;
  equipos: Equipo[];
  trimestres: Trimestre[];
  trimestre_actual: Trimestre | null;
}

export interface CompetenciaCreate {
  nombre: string;
  entidad_id: number;
  rubro_id: number;
  parametro_macro_id: number;
  parametro_rubro_id: number;
  num_trimestres: number;
  num_equipos_max?: number;
  max_integrantes_equipo?: number | null;
  caja_inicial: number;
  capacidad_inicial: number;
  headcount_inicial: number;
  salario_inicial: number;
  inventario_inicial?: number;
  valor_planta_inicial: number;
  escenario_id?: number;
  ia_habilitada?: boolean;
}

export interface CompetenciaUpdate {
  nombre?: string;
  caja_inicial?: number;
  capacidad_inicial?: number;
  headcount_inicial?: number;
  salario_inicial?: number;
  num_equipos_max?: number;
  max_integrantes_equipo?: number | null;
}

export type DificultadEscenario = 'FACIL' | 'NORMAL' | 'DIFICIL';

export interface EscenarioEvento {
  evento_catalogo_id: number;
  trimestre_numero: number;
}

export interface EscenarioPredefinido {
  id: number;
  nombre: string;
  descripcion: string;
  rubro_id: number;
  num_trimestres: number;
  caja_inicial: number;
  capacidad_inicial: number;
  headcount_inicial: number;
  salario_inicial: number;
  inventario_inicial: number;
  valor_planta_inicial: number;
  dificultad: DificultadEscenario;
  eventos: EscenarioEvento[];
}

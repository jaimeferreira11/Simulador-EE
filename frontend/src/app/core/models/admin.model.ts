import { Rol } from './usuario.model';

// ── Shared ──

export interface EstadoRequest {
  activo: boolean;
}

// ── Usuarios ──

export interface UsuarioRow {
  id: number;
  email: string;
  nombreCompleto: string;
  rol: Rol;
  activo: boolean;
  createdAt: string;
}

export interface UsuarioDetail {
  id: number;
  email: string;
  nombreCompleto: string;
  rol: Rol;
  activo: boolean;
  emailVerificado: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface UsuarioCreateRequest {
  email: string;
  password: string;
  nombreCompleto: string;
  rolCodigo: string;
}

export interface UsuarioUpdateRequest {
  nombreCompleto?: string;
  password?: string;
  rolCodigo?: string;
}

// ── Rubros ──

export interface RubroRow {
  id: number;
  codigo: string;
  nombre: string;
  activo: boolean;
  createdAt: string;
}

export interface RubroDetail {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string;
  activo: boolean;
  createdAt: string;
}

export interface RubroRequest {
  codigo: string;
  nombre: string;
  descripcion?: string;
}

// ── Parámetros Macro ──

export interface ParamMacroRow {
  id: number;
  nombreSet: string;
  vigenteDesde: string;
  activo: boolean;
  createdAt: string;
}

export interface ParamMacroDetail {
  id: number;
  nombreSet: string;
  vigenteDesde: string;
  salarioMinimoQ1: number;
  salarioMinimoQ4: number;
  ipsPatronal: number;
  ipsTrabajador: number;
  aguinaldoFactor: number;
  tasaIre: number;
  ivaGeneral: number;
  activo: boolean;
  createdAt: string;
}

export interface ParamMacroRequest {
  nombreSet: string;
  vigenteDesde?: string;
  salarioMinimoQ1: number;
  salarioMinimoQ4: number;
  ipsPatronal: number;
  ipsTrabajador: number;
  aguinaldoFactor: number;
  tasaIre: number;
  ivaGeneral: number;
}

export interface MacroTrimestreDto {
  id?: number;
  trimestre: number;
  inflacionTrim: number;
  tipoCambio: number;
  tpmAnual: number;
}

// ── Parámetros Rubro ──

export interface ParamRubroRow {
  id: number;
  codigo: string;
  rubroId: number;
  rubroNombre: string;
  demandaBaseTrim: number;
  precioReferencia: number;
  activo: boolean;
  createdAt: string;
}

export interface ParamRubroDetail {
  id: number;
  codigo: string;
  rubroId: number;
  rubroNombre: string;
  demandaBaseTrim: number;
  precioReferencia: number;
  elasticidadPrecio: number;
  elasticidadMarketing: number;
  elasticidadCalidad: number;
  pesoPrecio: number;
  pesoMarketing: number;
  pesoCalidad: number;
  pesoMarca: number;
  costoUnitMp: number;
  pctMpImportada: number;
  costosFijosTrim: number;
  depreciacionTrim: number;
  costoExpansionCapacidad: number;
  salarioPromedioSector: number;
  productividadEmpleado: number;
  brandEquityInicial: number;
  decaimientoBe: number;
  spreadTasa: number;
  activo: boolean;
  createdAt: string;
}

export interface ParamRubroRequest {
  codigo: string;
  rubroId: number;
  demandaBaseTrim: number;
  precioReferencia: number;
  elasticidadPrecio: number;
  elasticidadMarketing: number;
  elasticidadCalidad: number;
  pesoPrecio: number;
  pesoMarketing: number;
  pesoCalidad: number;
  pesoMarca: number;
  costoUnitMp: number;
  pctMpImportada: number;
  costosFijosTrim: number;
  depreciacionTrim: number;
  costoExpansionCapacidad: number;
  salarioPromedioSector: number;
  productividadEmpleado: number;
  brandEquityInicial: number;
  decaimientoBe: number;
  spreadTasa: number;
}

export interface RubroTrimestreDto {
  id?: number;
  trimestre: number;
  estacionalidad: number;
}

// ── Eventos Catálogo ──

export interface EventoRow {
  id: number;
  codigo: string;
  nombre: string;
  severidad: string;
  tipoEfecto: string;
  magnitudDefault: number;
  duracionQ: number;
  rubroId: number | null;
  rubroNombre: string | null;
  activo: boolean;
}

export interface EventoDetail {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string;
  severidad: string;
  tipoEfecto: string;
  magnitudDefault: number;
  duracionQ: number;
  requiereAnuncioPrevio: boolean;
  overridePesoPrecio: number | null;
  overridePesoMarketing: number | null;
  overridePesoCalidad: number | null;
  overridePesoMarca: number | null;
  rubroId: number | null;
  rubroNombre: string | null;
  activo: boolean;
}

export interface EventoRequest {
  codigo: string;
  nombre: string;
  descripcion?: string;
  severidad: string;
  tipoEfecto: string;
  magnitudDefault: number;
  duracionQ: number;
  requiereAnuncioPrevio: boolean;
  overridePesoPrecio?: number | null;
  overridePesoMarketing?: number | null;
  overridePesoCalidad?: number | null;
  overridePesoMarca?: number | null;
  rubroId?: number | null;
}

// ── Entidades ──

export interface EntidadRow {
  id: number;
  nombre: string;
  tipo: string;
  contactoEmail: string;
  activa: boolean;
  createdAt: string;
}

export interface EntidadDetail {
  id: number;
  nombre: string;
  tipo: string;
  descripcion: string;
  contactoNombre: string;
  contactoEmail: string;
  activa: boolean;
  createdAt: string;
}

export interface EntidadRequest {
  nombre: string;
  tipo: string;
  descripcion?: string;
  contactoNombre?: string;
  contactoEmail?: string;
}

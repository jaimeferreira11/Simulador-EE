export interface AreaDecision {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string;
}

export interface Rubro {
  id: number;
  codigo: string;
  nombre: string;
  descripcion: string;
  activo: boolean;
}

export interface ParametroMacro {
  id: number;
  nombre_set: string;
  vigente_desde: string;
  inflacion_trim_q1: number;
  inflacion_trim_q2: number;
  inflacion_trim_q3: number;
  inflacion_trim_q4: number;
  tipo_cambio_q1: number;
  tipo_cambio_q2: number;
  tipo_cambio_q3: number;
  tipo_cambio_q4: number;
  tpm_anual_q1: number;
  tpm_anual_q4: number;
  salario_minimo_q1: number;
  salario_minimo_q4: number;
  ips_patronal: number;
  ips_trabajador: number;
  aguinaldo_factor: number;
  tasa_ire: number;
  iva_general: number;
  activo: boolean;
}

export interface ParametroRubro {
  id: number;
  rubro_id: number;
  codigo: string;
  demanda_base_trim: number;
  precio_referencia: number;
  elasticidad_precio: number;
  elasticidad_marketing: number;
  elasticidad_calidad: number;
  peso_precio: number;
  peso_marketing: number;
  peso_calidad: number;
  peso_marca: number;
  costo_unit_mp: number;
  pct_mp_importada: number;
  costos_fijos_trim: number;
  depreciacion_trim: number;
  costo_expansion_capacidad: number;
  salario_promedio_sector: number;
  productividad_empleado: number;
  brand_equity_inicial: number;
  decaimiento_be: number;
  activo: boolean;
}

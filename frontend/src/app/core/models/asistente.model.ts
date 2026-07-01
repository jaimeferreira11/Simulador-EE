export type OrigenRespuesta = 'FAQ' | 'FALLBACK' | 'RAG' | 'CONTEXTO_PARTIDA';

export interface Fuente {
  titulo: string;
  ancla_manual: string;
}

export interface RespuestaAsistente {
  texto: string;
  fuentes: Fuente[];
  relacionadas: string[];
  origen: OrigenRespuesta;
}

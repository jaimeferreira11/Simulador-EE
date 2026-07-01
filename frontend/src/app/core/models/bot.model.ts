export type BotTipo = 'HUMANO' | 'BOT';
export type BotDificultad = 'FACIL' | 'MEDIO' | 'DIFICIL' | 'EXPERTO';
export type BotPersonalidad = 'COST_LEADER' | 'PREMIUM' | 'BALANCEADO';

export const BOT_DIFICULTAD_LABEL: Record<BotDificultad, string> = {
  FACIL: 'Fácil',
  MEDIO: 'Medio',
  DIFICIL: 'Difícil',
  EXPERTO: 'Experto',
};

export const BOT_DIFICULTAD_DESCRIPCION: Record<BotDificultad, string> = {
  FACIL: 'Sigue una estrategia fija. No reacciona a eventos. Buen rival para los primeros trimestres.',
  MEDIO: 'Ajusta producción según su inventario y reacciona a eventos importantes. Equivale a un equipo humano atento.',
  DIFICIL: 'Reacciona a todos los eventos, mira el ranking y ajusta su estrategia para superarte. Pone a prueba a los mejores equipos.',
  EXPERTO: 'Usa inteligencia artificial (LLM) para razonar cada decisión basándose en el contexto completo del trimestre. Reacciona a eventos novedosos, ajusta estrategia contra el ranking, considera el comportamiento esperado de cada competidor. Si el LLM falla, cae automáticamente al nivel Difícil. Pone a prueba a los equipos más experimentados y genera competencias dinámicas.',
};

export const BOT_PERSONALIDAD_LABEL: Record<BotPersonalidad, string> = {
  COST_LEADER: 'Líder en costos',
  PREMIUM: 'Premium',
  BALANCEADO: 'Balanceado',
};

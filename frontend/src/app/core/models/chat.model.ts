export interface ChatMensaje {
  id: number;
  usuario_id: number;
  nombre_usuario: string;
  contenido: string;
  created_at: string;
}

export interface ChatPage {
  content: ChatMensaje[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

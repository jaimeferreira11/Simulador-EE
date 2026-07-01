export type Rol = 'MODERADOR' | 'JUGADOR' | 'ADMIN_PLATAFORMA';

export interface Usuario {
  id: number;
  email: string;
  nombre_completo: string;
  rol: Rol;
  activo: boolean;
  email_verificado?: boolean;
  created_at?: string;
  last_login_at?: string | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface TokenResponse {
  access_token: string;
  refresh_token: string;
  token_type?: string;
  expires_in?: number;
  usuario?: Usuario;
}

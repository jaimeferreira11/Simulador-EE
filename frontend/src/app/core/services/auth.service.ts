import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TokenResponse, Usuario } from '../models/usuario.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private base = '/v1/auth';

  login(email: string, password: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${this.base}/login`, { email, password });
  }

  refresh(refreshToken: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(`${this.base}/refresh`, {
      refresh_token: refreshToken,
    });
  }

  me(): Observable<Usuario> {
    return this.http.get<Usuario>(`${this.base}/me`);
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.base}/logout`, {});
  }

  requestPasswordReset(email: string): Observable<void> {
    return this.http.post<void>(`${this.base}/password-reset/request`, { email });
  }

  confirmPasswordReset(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.base}/password-reset/confirm`, {
      token,
      new_password: newPassword,
    });
  }
}

import { Injectable, inject, signal, computed, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { Usuario, Rol } from '../models/usuario.model';

@Injectable({ providedIn: 'root' })
export class AuthStore implements OnDestroy {
  private authService = inject(AuthService);
  private router = inject(Router);

  user = signal<Usuario | null>(null);
  accessToken = signal<string | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);
  sessionExpired = signal(false);

  /** Resolves when initial session restore is complete (user loaded or no token). */
  readonly ready: Promise<void>;
  private resolveReady!: () => void;
  private initialized = false;
  private expiryCheckInterval: ReturnType<typeof setInterval> | null = null;

  isAuthenticated = computed(() => !!this.accessToken());
  rol = computed(() => this.user()?.rol);

  constructor() {
    this.ready = new Promise<void>((resolve) => {
      this.resolveReady = resolve;
    });
  }

  ngOnDestroy(): void {
    this.stopExpiryCheck();
  }

  login(email: string, password: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.sessionExpired.set(false);

    this.authService.login(email, password).subscribe({
      next: (res) => {
        this.accessToken.set(res.access_token);
        localStorage.setItem('access_token', res.access_token);
        localStorage.setItem('refresh_token', res.refresh_token);
        this.startExpiryCheck();
        this.loadUser(true);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.detail ?? 'Error de autenticación');
      },
    });
  }

  logout(): void {
    this.authService.logout().subscribe({
      complete: () => this.clearAndRedirect(),
      error: () => this.clearAndRedirect(),
    });
  }

  async tryRestoreSession(): Promise<void> {
    if (this.initialized) return;
    this.initialized = true;

    const token = localStorage.getItem('access_token');
    if (token) {
      if (this.isTokenExpired(token)) {
        this.clearStorage();
        this.resolveReady();
        return;
      }
      this.accessToken.set(token);
      try {
        const user = await firstValueFrom(this.authService.me());
        this.user.set(user);
        this.startExpiryCheck();
      } catch {
        this.accessToken.set(null);
        this.clearStorage();
      }
    }
    this.resolveReady();
  }

  updateTokens(accessToken: string, refreshToken: string): void {
    this.accessToken.set(accessToken);
    localStorage.setItem('access_token', accessToken);
    localStorage.setItem('refresh_token', refreshToken);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem('refresh_token');
  }

  redirectByRol(rol: Rol): void {
    switch (rol) {
      case 'ADMIN_PLATAFORMA':
        this.router.navigate(['/admin/usuarios']);
        break;
      case 'MODERADOR':
        this.router.navigate(['/moderador/dashboard']);
        break;
      case 'JUGADOR':
        this.router.navigate(['/jugador/seleccionar-competencia']);
        break;
    }
  }

  private loadUser(redirect: boolean): void {
    this.authService.me().subscribe({
      next: (user) => {
        this.user.set(user);
        this.loading.set(false);
        if (redirect) {
          this.redirectByRol(user.rol);
        }
      },
      error: () => {
        this.loading.set(false);
        this.clearAndRedirect();
      },
    });
  }

  /** Called when session expires (token expiry or failed refresh). */
  logoutExpired(): void {
    this.stopExpiryCheck();
    this.user.set(null);
    this.accessToken.set(null);
    this.clearStorage();
    this.sessionExpired.set(true);
    this.router.navigate(['/login']);
  }

  private clearAndRedirect(): void {
    this.stopExpiryCheck();
    this.user.set(null);
    this.accessToken.set(null);
    this.clearStorage();
    this.router.navigate(['/login']);
  }

  private clearStorage(): void {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    this.clearConfettiFlags();
  }

  private clearConfettiFlags(): void {
    const keys = Object.keys(sessionStorage).filter(k => k.startsWith('confetti_shown_'));
    keys.forEach(k => sessionStorage.removeItem(k));
  }

  /** Decode JWT payload and check if expired. No network call. */
  private isTokenExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp * 1000 <= Date.now();
    } catch {
      return true;
    }
  }

  /** Returns seconds until the access token expires, or 0 if expired/invalid. */
  private getTokenSecondsRemaining(): number {
    const token = this.accessToken();
    if (!token) return 0;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return Math.max(0, payload.exp - Math.floor(Date.now() / 1000));
    } catch {
      return 0;
    }
  }

  /** Check token expiry every 30s. Cheap — only reads the local JWT, no API calls. */
  private startExpiryCheck(): void {
    this.stopExpiryCheck();
    this.expiryCheckInterval = setInterval(() => {
      const remaining = this.getTokenSecondsRemaining();
      if (remaining <= 0 && this.isAuthenticated()) {
        // Try refresh before logging out
        const refreshToken = this.getRefreshToken();
        if (refreshToken) {
          this.authService.refresh(refreshToken).subscribe({
            next: (tokens) => {
              this.updateTokens(tokens.access_token, tokens.refresh_token);
            },
            error: () => {
              this.logoutExpired();
            },
          });
        } else {
          this.logoutExpired();
        }
      }
    }, 30_000);
  }

  private stopExpiryCheck(): void {
    if (this.expiryCheckInterval) {
      clearInterval(this.expiryCheckInterval);
      this.expiryCheckInterval = null;
    }
  }
}

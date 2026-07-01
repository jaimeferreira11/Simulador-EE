import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthStore } from '../stores/auth.store';
import { AuthService } from '../services/auth.service';

let isRefreshing = false;

const AUTH_URLS = ['/auth/login', '/auth/refresh', '/auth/register', '/invitaciones/token/'];

export const refreshInterceptor: HttpInterceptorFn = (req, next) => {
  const authStore = inject(AuthStore);
  const authService = inject(AuthService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const isAuthUrl = AUTH_URLS.some((url) => req.url.includes(url));

      if ((error.status === 401 || error.status === 403) && !isAuthUrl) {
        const refreshToken = authStore.getRefreshToken();
        if (refreshToken && !isRefreshing) {
          isRefreshing = true;
          return authService.refresh(refreshToken).pipe(
            switchMap((tokens) => {
              isRefreshing = false;
              authStore.updateTokens(tokens.access_token, tokens.refresh_token);
              const retryReq = req.clone({
                setHeaders: { Authorization: `Bearer ${tokens.access_token}` },
              });
              return next(retryReq);
            }),
            catchError((refreshError) => {
              isRefreshing = false;
              authStore.logoutExpired();
              return throwError(() => refreshError);
            }),
          );
        }

        if (!refreshToken) {
          authStore.logoutExpired();
        }
      }
      return throwError(() => error);
    }),
  );
};

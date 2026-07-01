import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthStore } from '../stores/auth.store';

const PUBLIC_URLS = [
  '/v1/auth/login',
  '/v1/auth/refresh',
  '/v1/auth/password-reset',
  '/v1/invitaciones/token/',
];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authStore = inject(AuthStore);
  const token = authStore.accessToken();

  const isPublic = PUBLIC_URLS.some((url) => req.url.includes(url));

  if (token && !isPublic) {
    const cloned = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    });
    return next(cloned);
  }

  return next(req);
};

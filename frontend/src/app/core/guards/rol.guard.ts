import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthStore } from '../stores/auth.store';
import { Rol } from '../models/usuario.model';

export const rolGuard = (...allowedRoles: Rol[]): CanActivateFn => {
  return async () => {
    const authStore = inject(AuthStore);
    const router = inject(Router);

    await authStore.ready;

    const userRol = authStore.rol();
    if (userRol && allowedRoles.includes(userRol)) {
      return true;
    }

    return router.createUrlTree(['/login']);
  };
};

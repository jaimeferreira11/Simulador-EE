import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthStore } from '../stores/auth.store';
import { CompetenciaStore } from '../stores/competencia.store';
import { DemoService } from '../services/demo.service';

/**
 * Combined role + demo-CEO guard for the jugador layout subtree.
 *
 * Pass conditions:
 *   - JUGADOR (anyone with the player role goes through unchanged).
 *   - MODERADOR or ADMIN_PLATAFORMA *and* the active competencia is DEMO
 *     *and* DemoService.modoCeoActivo() is true.
 *
 * Everything else is redirected: unauthenticated → /login,
 * other roles → /moderador (their natural landing area).
 *
 * The check on the active competencia uses CompetenciaStore.competenciaActiva,
 * which is populated as soon as the moderador navigates into a competencia.
 */
export const demoCeoModeGuard: CanActivateFn = async () => {
  const auth = inject(AuthStore);
  const competenciaStore = inject(CompetenciaStore);
  const demo = inject(DemoService);
  const router = inject(Router);

  await auth.ready;

  const rol = auth.rol();
  if (rol === 'JUGADOR') return true;

  if (rol !== 'MODERADOR' && rol !== 'ADMIN_PLATAFORMA') {
    return router.createUrlTree(['/login']);
  }

  const comp = competenciaStore.competenciaActiva();
  if (demo.isDemo(comp) && demo.modoCeoActivo()) {
    return true;
  }

  return router.createUrlTree(['/moderador']);
};

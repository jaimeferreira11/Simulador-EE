import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthStore } from '../../core/stores/auth.store';

export interface OnboardingStep {
  /** 1-based index */
  step: number;
  /** CSS selector for the highlighted element (data-tutorial attribute) */
  selector: string;
  /** Optional route to navigate to BEFORE showing this step */
  route?: string;
  title: string;
  body: string;
}

/**
 * Onboarding tutorial steps for the jugador role.
 * Each step optionally navigates to the right route, then highlights an element.
 */
export const JUGADOR_ONBOARDING_STEPS: OnboardingStep[] = [
  {
    step: 1,
    selector: '[data-tutorial="mis-competencias"]',
    route: '/jugador/competencia',
    title: '¡Bienvenido a Simulador!',
    body: 'Acá ves todas las competencias en las que participás. Hacé clic en una activa para entrar.',
  },
  {
    step: 2,
    selector: '[data-tutorial="equipo"]',
    route: '/jugador/equipo',
    title: 'Tu equipo',
    body: 'Estos son tus compañeros. Cada uno puede asignarse a un área funcional (Finanzas, Marketing, etc).',
  },
  {
    step: 3,
    selector: '[data-tutorial="decisiones"]',
    route: '/jugador/decisiones',
    title: 'Las decisiones del trimestre',
    body: 'Acá vas a cargar las ~10-12 decisiones del trimestre: precio, producción, marketing, I+D. El sistema valida en tiempo real que tu caja no quede negativa.',
  },
  {
    step: 4,
    selector: '[data-tutorial="noticias"]',
    route: '/jugador/noticias',
    title: 'Noticias del mercado',
    body: 'Eventos como subas de combustible o promociones aparecen acá. Pueden cambiar las reglas del trimestre.',
  },
  {
    step: 5,
    selector: '[data-tutorial="resultados"]',
    route: '/jugador/resultados',
    title: 'Tus resultados',
    body: 'Al cerrar el trimestre, acá ves cuánto vendiste, tu ganancia y tu posición. También un feedback automático.',
  },
  {
    step: 6,
    selector: '[data-tutorial="ranking"]',
    route: '/jugador/rankings',
    title: 'El ranking',
    body: 'Compará tu PIP (Performance Index) con los demás equipos. Algunos pueden ser bots.',
  },
  {
    step: 7,
    selector: '[data-tutorial="chat"]',
    route: '/jugador/chat',
    title: 'Chat de equipo',
    body: 'Coordiná decisiones con tus compañeros. Tu equipo puede chatear en tiempo real.',
  },
];

const STORAGE_PREFIX = 'simulador:onboarding:done:';

@Injectable({ providedIn: 'root' })
export class OnboardingService {
  private router = inject(Router);
  private authStore = inject(AuthStore);

  private readonly _steps = signal<OnboardingStep[]>(JUGADOR_ONBOARDING_STEPS);
  private readonly _currentIndex = signal(0);
  private readonly _isActive = signal(false);

  readonly steps = computed(() => this._steps());
  readonly currentIndex = computed(() => this._currentIndex());
  readonly total = computed(() => this._steps().length);
  readonly isActive = computed(() => this._isActive());
  readonly currentStep = computed<OnboardingStep | null>(() => {
    if (!this._isActive()) return null;
    return this._steps()[this._currentIndex()] ?? null;
  });
  readonly isLast = computed(() => this._currentIndex() === this._steps().length - 1);

  /** Returns the storage key scoped to the current user (or 'anon'). */
  private storageKey(): string {
    const userId = this.authStore.user()?.id ?? 'anon';
    return STORAGE_PREFIX + userId;
  }

  /** Whether the current user has already completed the tutorial. */
  hasCompleted(): boolean {
    try {
      return localStorage.getItem(this.storageKey()) === '1';
    } catch {
      return false;
    }
  }

  /** Trigger the tutorial if user is JUGADOR and has not completed it. */
  maybeStart(): void {
    if (this.authStore.rol() !== 'JUGADOR') return;
    if (this.hasCompleted()) return;
    if (this._isActive()) return;
    this.start();
  }

  /** Start (or restart) the tutorial. Clears the completed flag. */
  start(): void {
    try {
      localStorage.removeItem(this.storageKey());
    } catch {
      /* noop */
    }
    this._currentIndex.set(0);
    this._isActive.set(true);
    this.navigateForCurrent();
  }

  next(): void {
    if (!this._isActive()) return;
    if (this._currentIndex() >= this._steps().length - 1) {
      this.complete();
      return;
    }
    this._currentIndex.update((i) => i + 1);
    this.navigateForCurrent();
  }

  prev(): void {
    if (!this._isActive()) return;
    if (this._currentIndex() === 0) return;
    this._currentIndex.update((i) => i - 1);
    this.navigateForCurrent();
  }

  skip(): void {
    this.complete();
  }

  complete(): void {
    try {
      localStorage.setItem(this.storageKey(), '1');
    } catch {
      /* noop */
    }
    this._isActive.set(false);
    this._currentIndex.set(0);
  }

  private navigateForCurrent(): void {
    const step = this._steps()[this._currentIndex()];
    if (step?.route && !this.router.url.startsWith(step.route)) {
      this.router.navigateByUrl(step.route);
    }
  }
}

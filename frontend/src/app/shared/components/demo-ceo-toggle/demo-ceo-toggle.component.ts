import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ArrowLeftRight, LucideAngularModule, User } from 'lucide-angular';
import { AuthStore } from '../../../core/stores/auth.store';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { DemoService } from '../../../core/services/demo.service';

/**
 * Toggle que el moderador usa durante la presentación de la competencia DEMO
 * para entrar y salir del "Modo CEO" — la vista del jugador del equipo HUMANO.
 *
 * Solo se muestra cuando la competencia activa tiene {@code codigo='DEMO'}.
 * El flag se persiste en {@link DemoService.modoCeoActivo} (sessionStorage).
 *
 * La navegación a la vista de jugador apunta a la ruta plana {@code /jugador/decisiones}.
 * La resolución del equipo HUMANO en el {@code JugadorStore} la maneja el wiring
 * de la pantalla del jugador en Phase 8 (no este componente).
 */
@Component({
  selector: 'app-demo-ceo-toggle',
  standalone: true,
  imports: [LucideAngularModule],
  template: `
    @if (visible()) {
      <button
        type="button"
        (click)="toggle()"
        class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md border border-primary
               text-primary bg-white hover:bg-primary-light text-sm font-body font-medium
               transition-colors"
      >
        <lucide-icon [img]="iconRef()" [size]="16"></lucide-icon>
        <span>{{ activo() ? 'Salir del Modo CEO' : 'Entrar a Modo CEO' }}</span>
      </button>
    }
  `,
})
export class DemoCeoToggleComponent {
  private compStore = inject(CompetenciaStore);
  private auth = inject(AuthStore);
  private demo = inject(DemoService);
  private router = inject(Router);

  readonly UserIcon = User;
  readonly ArrowIcon = ArrowLeftRight;

  /**
   * Visible solo cuando:
   *   - la competencia activa es la DEMO, y
   *   - el usuario logueado puede actuar como CEO (moderador o admin).
   * Un JUGADOR real nunca ve este botón — no tiene un "Modo CEO" para alternar;
   * el banner amarillo le alcanza como contexto.
   */
  visible = computed(() => {
    if (!this.demo.isDemo(this.compStore.competenciaActiva())) return false;
    const rol = this.auth.rol();
    return rol === 'MODERADOR' || rol === 'ADMIN_PLATAFORMA';
  });
  activo = computed(() => this.demo.modoCeoActivo());
  iconRef = computed(() => (this.activo() ? this.ArrowIcon : this.UserIcon));

  toggle(): void {
    const next = !this.demo.modoCeoActivo();
    this.demo.setModoCeoActivo(next);

    if (next) {
      // Entrar a Modo CEO → vista de jugador (decisiones del equipo HUMANO).
      // El JugadorStore se hidrata desde la vista (resolución del equipo HUMANO en wiring).
      this.router.navigate(['/jugador/decisiones']);
    } else {
      // Volver al dashboard de la competencia del moderador.
      this.router.navigate(['/moderador/competencia']);
    }
  }
}

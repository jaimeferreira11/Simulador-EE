import { Component, input, output } from '@angular/core';
import { FastForward, LucideAngularModule } from 'lucide-angular';

/**
 * Floating Action Button para avanzar el trimestre actual en modo demo.
 * Persiste mientras haya un trimestre {@code ABIERTO_DECISIONES} y el CEO
 * haya pospuesto el avance vía el diálogo.
 *
 * El componente es puramente presentacional — emite {@code (advance)} al click
 * y el host decide si llamar al endpoint, mostrar toast, recargar, etc.
 */
@Component({
  selector: 'app-advance-trimester-fab',
  standalone: true,
  imports: [LucideAngularModule],
  template: `
    @if (visible()) {
      <button
        type="button"
        [disabled]="loading()"
        (click)="advance.emit()"
        class="fixed bottom-6 right-6 z-40 inline-flex items-center gap-2 px-5 py-3
               rounded-full bg-primary text-white shadow-lg font-body text-sm font-medium
               hover:bg-primary-dark disabled:opacity-60 disabled:cursor-not-allowed
               transition-colors"
      >
        <lucide-icon [img]="FastForwardIcon" [size]="18"></lucide-icon>
        <span>{{ loading() ? 'Avanzando…' : 'Avanzar trimestre' }}</span>
      </button>
    }
  `,
})
export class AdvanceTrimesterFabComponent {
  visible = input<boolean>(false);
  loading = input<boolean>(false);
  advance = output<void>();

  readonly FastForwardIcon = FastForward;
}

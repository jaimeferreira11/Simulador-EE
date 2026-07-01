import { Component, computed, inject, signal } from '@angular/core';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { LucideAngularModule, RefreshCcw } from 'lucide-angular';
import { firstValueFrom } from 'rxjs';
import { ConfirmDialogComponent, ConfirmDialogData } from '../confirm-dialog/confirm-dialog.component';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { DemoService } from '../../../core/services/demo.service';

/**
 * Botón rojo "Reiniciar demo" que aparece en el header de la competencia DEMO.
 * Pide confirmación vía {@link ConfirmDialogComponent}; al confirmar llama
 * {@code POST /demo/reiniciar} y refresca la competencia en el store.
 */
@Component({
  selector: 'app-reset-demo-button',
  standalone: true,
  imports: [LucideAngularModule, MatDialogModule],
  template: `
    @if (visible()) {
      <button
        type="button"
        [disabled]="loading()"
        (click)="askConfirm()"
        class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md border border-danger
               text-danger bg-white hover:bg-danger-light text-sm font-body font-medium
               transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
      >
        <lucide-icon [img]="RefreshIcon" [size]="16"></lucide-icon>
        <span>{{ loading() ? 'Reiniciando…' : 'Reiniciar demo' }}</span>
      </button>
    }
  `,
})
export class ResetDemoButtonComponent {
  private compStore = inject(CompetenciaStore);
  private demo = inject(DemoService);
  private dialog = inject(MatDialog);

  readonly RefreshIcon = RefreshCcw;

  loading = signal(false);

  visible = computed(() => this.demo.isDemo(this.compStore.competenciaActiva()));

  async askConfirm(): Promise<void> {
    const data: ConfirmDialogData = {
      title: 'Reiniciar Demo',
      message:
        'Esto borrará todas las decisiones, resultados, rankings y eventos. ' +
        'Los equipos y miembros se mantienen. La competencia vuelve a Q1 con bots listos.',
      confirmText: 'Sí, reiniciar',
      cancelText: 'Cancelar',
      danger: true,
    };
    const ref = this.dialog.open(ConfirmDialogComponent, { data, width: '480px' });
    const confirmed = await firstValueFrom(ref.afterClosed());
    if (!confirmed) return;

    const comp = this.compStore.competenciaActiva();
    if (!comp) return;
    this.loading.set(true);
    this.demo.reiniciar(comp.id).subscribe({
      next: async () => {
        await this.compStore.reload();
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      },
    });
  }
}

import { Component, inject, computed } from '@angular/core';
import { Router } from '@angular/router';
import { LucideAngularModule, ChevronsUpDown } from 'lucide-angular';
import { JugadorStore } from '../../../core/stores/jugador.store';

@Component({
  selector: 'app-comp-switcher',
  standalone: true,
  imports: [LucideAngularModule],
  template: `
    @if (visible()) {
      <button
        (click)="goToSelector()"
        class="w-full rounded-md bg-surface-card border border-border p-2.5 flex flex-col gap-1 cursor-pointer hover:border-primary/30 transition-colors text-left"
      >
        <div class="flex items-center justify-between w-full">
          <div class="flex flex-col gap-0.5 min-w-0">
            <span class="font-label text-[9px] font-semibold tracking-[1.2px] text-text-secondary uppercase">COMPETENCIA</span>
            <span class="font-body text-[13px] font-semibold text-text-primary truncate">{{ nombre() }}</span>
          </div>
          <lucide-icon [img]="iconChevrons" [size]="16" class="text-text-secondary shrink-0"></lucide-icon>
        </div>
        <div class="flex items-center gap-2">
          <span
            class="w-1.5 h-1.5 rounded-full"
            [class]="esEnCurso() ? 'bg-active-dot' : 'bg-gray-400'"
          ></span>
          <span class="font-body text-[11px] text-text-secondary">{{ estadoLabel() }}</span>
        </div>
      </button>
    }
  `,
})
export class CompSwitcherComponent {
  readonly iconChevrons = ChevronsUpDown;

  private jugadorStore = inject(JugadorStore);
  private router = inject(Router);

  visible = computed(() => {
    const todas = this.jugadorStore.misCompetencias();
    const relevantes = todas.filter(c =>
      c.estado === 'EN_CURSO' ||
      c.estado === 'PENDIENTE_FINALIZAR' ||
      c.estado === 'FINALIZADA' ||
      c.estado === 'ABIERTA_INSCRIPCION'
    );
    return relevantes.length > 1;
  });

  nombre = computed(() => this.jugadorStore.competencia()?.nombre ?? '');

  esEnCurso = computed(() => this.jugadorStore.competencia()?.estado === 'EN_CURSO');

  estadoLabel = computed(() => {
    const comp = this.jugadorStore.competencia();
    if (!comp) return '';
    const trimestre = this.jugadorStore.trimestreActual();
    const qLabel = trimestre ? `Q${trimestre.numero}` : '';
    const estadoMap: Record<string, string> = {
      EN_CURSO: `En Curso${qLabel ? ' · ' + qLabel : ''}`,
      FINALIZADA: 'Finalizada',
      PENDIENTE_FINALIZAR: 'Pendiente Finalizar',
      ABIERTA_INSCRIPCION: 'Inscripcion',
    };
    return estadoMap[comp.estado] ?? comp.estado;
  });

  goToSelector(): void {
    this.router.navigate(['/jugador/seleccionar-competencia']);
  }
}

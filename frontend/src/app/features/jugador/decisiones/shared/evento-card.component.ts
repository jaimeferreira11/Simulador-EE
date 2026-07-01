import { Component, input, computed } from '@angular/core';
import { LucideAngularModule, TriangleAlert, Zap, TrendingUp, LucideIconData } from 'lucide-angular';
import { EventoActivo, AREA_LABELS } from '../../../../core/models/contexto-decision.model';

@Component({
  selector: 'app-evento-card',
  standalone: true,
  imports: [LucideAngularModule],
  template: `
    <div class="rounded-lg border bg-white p-4" [style.border-color]="borderColor()">
      <div class="flex items-center gap-2 mb-2">
        <lucide-icon [img]="iconImg()" [size]="16" [style.color]="borderColor()"></lucide-icon>
        <span class="font-label text-xs font-semibold tracking-wide uppercase" [style.color]="borderColor()">
          {{ evento().nombre }} — {{ evento().severidad }}
        </span>
      </div>
      <p class="font-body text-sm text-text-icon leading-relaxed mb-2">
        {{ evento().descripcion }}
      </p>
      <div class="flex items-center justify-between">
        <div class="flex gap-1.5">
          @for (area of evento().areas_impactadas; track area) {
            <span class="font-label text-[10px] font-semibold px-1.5 py-0.5 rounded"
                  [style.background-color]="borderColor() + '20'" [style.color]="borderColor()">
              {{ areaLabel(area) }}
            </span>
          }
        </div>
        <span class="font-label text-xs text-text-secondary">
          {{ evento().duracion_restante }}Q restante{{ evento().duracion_restante > 1 ? 's' : '' }}
        </span>
      </div>
    </div>
  `,
})
export class EventoCardComponent {
  evento = input.required<EventoActivo>();

  private readonly iconMap: Record<string, LucideIconData> = {
    GRAVE: TriangleAlert,
    MODERADO: Zap,
    POSITIVO: TrendingUp,
  };

  borderColor = computed(() => {
    switch (this.evento().severidad) {
      case 'GRAVE': return '#DC2626';
      case 'MODERADO': return '#D97706';
      case 'POSITIVO': return '#16A34A';
    }
  });

  bgColor = computed(() => {
    switch (this.evento().severidad) {
      case 'GRAVE': return '#FEF2F2';
      case 'MODERADO': return '#FEF3C7';
      case 'POSITIVO': return '#DCFCE7';
    }
  });

  iconImg = computed<LucideIconData>(() => this.iconMap[this.evento().severidad] ?? Zap);

  areaLabel(area: string): string {
    return AREA_LABELS[area as keyof typeof AREA_LABELS] ?? area;
  }
}

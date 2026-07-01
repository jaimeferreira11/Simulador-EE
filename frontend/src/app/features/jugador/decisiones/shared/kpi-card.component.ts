import { Component, input } from '@angular/core';
import { LucideAngularModule, LucideIconData } from 'lucide-angular';

@Component({
  selector: 'app-kpi-card',
  standalone: true,
  imports: [LucideAngularModule],
  template: `
    <div class="sim-card flex flex-col gap-1.5">
      <div class="flex items-center gap-2">
        <lucide-icon [img]="icon()" [size]="16" [style.color]="color()"></lucide-icon>
        <span class="sim-stat-label">{{ label() }}</span>
      </div>
      <span class="font-display text-xl text-text-primary">{{ value() }}</span>
      @if (delta()) {
        <span class="font-label text-xs"
              [class.text-green-600]="deltaPositive()"
              [class.text-red-600]="!deltaPositive()">
          {{ delta() }}
        </span>
      }
    </div>
  `,
})
export class KpiCardComponent {
  label = input.required<string>();
  value = input.required<string>();
  icon = input.required<LucideIconData>();
  color = input<string>('#006B3F');
  delta = input<string>('');

  deltaPositive(): boolean {
    return this.delta().startsWith('+');
  }
}

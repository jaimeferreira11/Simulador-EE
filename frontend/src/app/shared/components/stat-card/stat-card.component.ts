import { Component, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-stat-card',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <mat-card class="p-4">
      <div class="text-sm text-gray-500 mb-1">{{ label() }}</div>
      <div class="text-2xl font-bold">{{ value() }}</div>
      @if (subtitle()) {
        <div class="text-xs mt-1" [class]="subtitleColor()">{{ subtitle() }}</div>
      }
    </mat-card>
  `,
})
export class StatCardComponent {
  label = input.required<string>();
  value = input.required<string>();
  subtitle = input<string>();
  subtitleColor = input<string>('text-gray-400');
}

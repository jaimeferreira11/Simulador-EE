import { Component, input } from '@angular/core';
import { LucideAngularModule, Inbox } from 'lucide-angular';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [LucideAngularModule],
  template: `
    <div class="flex flex-col items-center justify-center py-16 text-text-secondary">
      <lucide-icon [img]="icon()" [size]="48" class="mb-4 opacity-40"></lucide-icon>
      <p class="text-lg font-body">{{ message() }}</p>
      @if (submessage()) {
        <p class="text-sm font-body mt-1 opacity-70">{{ submessage() }}</p>
      }
      <div class="mt-4">
        <ng-content />
      </div>
    </div>
  `,
})
export class EmptyStateComponent {
  icon = input(Inbox);
  message = input('No hay datos');
  submessage = input('');
}

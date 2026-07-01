import { Component, input } from '@angular/core';

@Component({
  selector: 'app-page-header',
  standalone: true,
  template: `
    <div class="flex items-center justify-between mb-6">
      <div>
        @if (breadcrumb()) {
          <div class="text-sm text-gray-500 mb-1">{{ breadcrumb() }}</div>
        }
        <h1 class="text-2xl font-bold">{{ title() }}</h1>
        @if (subtitle()) {
          <p class="text-sm text-gray-500 mt-1">{{ subtitle() }}</p>
        }
      </div>
      <div class="flex gap-2">
        <ng-content />
      </div>
    </div>
  `,
})
export class PageHeaderComponent {
  title = input.required<string>();
  breadcrumb = input<string>();
  subtitle = input<string>();
}

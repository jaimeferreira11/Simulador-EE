import { Component, inject, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, ChevronRight } from 'lucide-angular';
import { BreadcrumbService } from '../../../core/services/breadcrumb.service';

interface CrumbView {
  label: string;
  path: string;
  queryParams: Record<string, string>;
}

@Component({
  selector: 'app-breadcrumb',
  standalone: true,
  imports: [RouterLink, LucideAngularModule],
  template: `
    @if (crumbs().length > 1) {
      <nav class="flex items-center gap-1 mb-4 text-xs font-body">
        @for (crumb of crumbs(); track $index) {
          @if ($index > 0) {
            <lucide-icon [img]="icons.ChevronRight" [size]="12" class="text-text-secondary/40"></lucide-icon>
          }
          @if (!$last) {
            <a [routerLink]="crumb.path"
               [queryParams]="crumb.queryParams"
               class="text-text-secondary hover:text-primary transition-colors">
              {{ crumb.label }}
            </a>
          } @else {
            <span class="text-text-primary font-medium">{{ crumb.label }}</span>
          }
        }
      </nav>
    }
  `,
})
export class BreadcrumbComponent {
  readonly icons = { ChevronRight };
  private breadcrumbService = inject(BreadcrumbService);

  crumbs = computed<CrumbView[]>(() => {
    return this.breadcrumbService.trail().map(item => {
      const [path, qs] = item.url.split('?');
      const queryParams: Record<string, string> = {};
      if (qs) {
        qs.split('&').forEach(pair => {
          const [k, v] = pair.split('=');
          if (k) queryParams[decodeURIComponent(k)] = decodeURIComponent(v ?? '');
        });
      }
      return { label: item.label, path, queryParams };
    });
  });
}

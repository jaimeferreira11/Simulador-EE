import { Component } from '@angular/core';

@Component({
  selector: 'app-dashboard-placeholder',
  standalone: true,
  template: `
    <div class="flex items-center justify-center h-64">
      <div class="text-center text-gray-400">
        <p class="text-xl">Dashboard</p>
        <p class="text-sm mt-2">Se implementará en la siguiente fase</p>
      </div>
    </div>
  `,
})
export class DashboardPlaceholderComponent {}

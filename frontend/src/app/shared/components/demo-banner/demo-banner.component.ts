import { Component, computed, inject } from '@angular/core';
import { Sparkles, LucideAngularModule } from 'lucide-angular';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { DemoService } from '../../../core/services/demo.service';

/**
 * Banner amarillo full-width que se muestra siempre que la competencia
 * activa sea la DEMO. Sirve para que el moderador y los espectadores de
 * la presentación recuerden que es una competencia que se reinicia.
 */
@Component({
  selector: 'app-demo-banner',
  standalone: true,
  imports: [LucideAngularModule],
  template: `
    @if (visible()) {
      <div
        role="alert"
        class="w-full bg-yellow-100 text-yellow-900 border-b border-yellow-400 px-4 py-2 text-sm font-body flex items-center justify-center gap-2"
      >
        <lucide-icon [img]="SparklesIcon" [size]="16" class="text-yellow-700"></lucide-icon>
        <strong class="font-semibold">MODO DEMO</strong>
        <span class="opacity-80">·</span>
        <span>Esta es la competencia DEMO. Los datos se reinician cuando el moderador lo decide.</span>
      </div>
    }
  `,
})
export class DemoBannerComponent {
  private compStore = inject(CompetenciaStore);
  private demo = inject(DemoService);

  readonly SparklesIcon = Sparkles;

  visible = computed(() => this.demo.isDemo(this.compStore.competenciaActiva()));
}

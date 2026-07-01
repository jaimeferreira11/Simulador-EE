import { Component, computed, input } from '@angular/core';
import { Bot, LucideAngularModule } from 'lucide-angular';
import {
  BOT_DIFICULTAD_LABEL,
  BOT_PERSONALIDAD_LABEL,
  BotDificultad,
  BotPersonalidad,
} from '../../../core/models/bot.model';

/**
 * Pequeño chip que identifica a un equipo automatizado (BOT).
 * Muestra "Bot" + dificultad opcional, con tooltip que combina
 * dificultad y personalidad cuando estan disponibles.
 *
 * Sigue el estilo Tailwind de los demas chips de la app (ver EstadoChipComponent).
 */
@Component({
  selector: 'app-bot-badge',
  standalone: true,
  imports: [LucideAngularModule],
  template: `
    <span
      class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-700 text-[11px] font-label font-semibold align-middle"
      [title]="tooltipText()"
    >
      <lucide-icon [img]="BotIcon" [size]="12"></lucide-icon>
      <span>Bot</span>
      @if (dificultad()) {
        <span class="opacity-70 font-normal">{{ DIFICULTAD_LABEL[dificultad()!] }}</span>
      }
    </span>
  `,
})
export class BotBadgeComponent {
  dificultad = input<BotDificultad | null | undefined>(null);
  personalidad = input<BotPersonalidad | null | undefined>(null);

  readonly BotIcon = Bot;
  readonly DIFICULTAD_LABEL = BOT_DIFICULTAD_LABEL;

  tooltipText = computed(() => {
    const d = this.dificultad();
    const p = this.personalidad();
    if (d && p) return `Bot ${BOT_DIFICULTAD_LABEL[d]} · ${BOT_PERSONALIDAD_LABEL[p]}`;
    if (d) return `Bot ${BOT_DIFICULTAD_LABEL[d]}`;
    if (p) return `Bot · ${BOT_PERSONALIDAD_LABEL[p]}`;
    return 'Equipo automatizado';
  });
}

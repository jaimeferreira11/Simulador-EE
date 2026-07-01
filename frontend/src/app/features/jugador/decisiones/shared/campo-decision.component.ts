import { Component, input, output, computed, signal, OnInit, OnChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Info } from 'lucide-angular';
import { CampoMeta, AreaDecisionV2, AREA_COLORS } from '../../../../core/models/contexto-decision.model';

@Component({
  selector: 'app-campo-decision',
  standalone: true,
  imports: [FormsModule, LucideAngularModule],
  template: `
    <div class="rounded-lg border border-border/60 p-5 bg-surface-card/50">
      <!-- Header: label (left) + Q anterior + delta (right) -->
      <div class="flex items-center justify-between mb-3">
        <span class="font-label text-xs font-semibold tracking-wide uppercase text-text-primary">
          {{ campo().label }}
        </span>
        @if (valorAnterior() != null) {
          <div class="flex items-center gap-2">
            <span class="font-label text-xs text-text-secondary">
              Q anterior: {{ formatVal(valorAnterior()) }}
            </span>
            @if (deltaText()) {
              <span class="font-label text-xs font-semibold"
                    [class.text-green-600]="deltaPositive()"
                    [class.text-red-600]="!deltaPositive()">
                {{ deltaText() }}
              </span>
            }
          </div>
        }
      </div>

      <!-- Input -->
      <div class="flex items-center gap-2 rounded-md h-11 px-3.5 border bg-white transition-colors"
           [style.border-color]="disabled() ? '#E2E4E8' : '#D1D5DB'">
        @if (campo().unidad === 'guarani') {
          <span class="font-label text-sm text-text-secondary">Gs</span>
        }
        @if (esGuarani()) {
          <input
            type="text"
            inputmode="numeric"
            class="flex-1 bg-transparent outline-none font-body text-sm text-text-primary"
            [class.text-text-secondary]="disabled()"
            [placeholder]="campo().placeholder"
            [disabled]="disabled()"
            [value]="displayValue()"
            (input)="onGuaraniInput($event)"
            (focus)="onFocus()"
            (blur)="onBlur()"
          />
        } @else {
          <input
            type="number"
            class="flex-1 bg-transparent outline-none font-body text-sm text-text-primary"
            [class.text-text-secondary]="disabled()"
            [placeholder]="campo().placeholder"
            [disabled]="disabled()"
            [ngModel]="value()"
            (ngModelChange)="onValueChange($event)"
          />
        }
        @if (campo().unidad === 'unidades') {
          <span class="font-label text-xs text-text-secondary">uds</span>
        }
        @if (campo().unidad === 'porcentaje') {
          <span class="font-label text-xs text-text-secondary">%</span>
        }
        @if (campo().unidad === 'personas') {
          <span class="font-label text-xs text-text-secondary">personas</span>
        }
      </div>

      <!-- Helper text (context hint) -->
      @if (campo().helper && !disabled()) {
        <div class="mt-2 text-xs text-text-secondary font-body">
          {{ campo().helper }}
        </div>
      }

      <!-- Tooltip educativo — always visible -->
      @if (campo().tooltip && !disabled()) {
        <div class="mt-3 rounded-md px-3 py-2.5 flex items-start gap-2"
             [style.background-color]="colors().bg">
          <lucide-icon [img]="icn.Info" [size]="14" [style.color]="colors().main" class="shrink-0 mt-0.5"></lucide-icon>
          <span class="font-body text-xs leading-relaxed" style="color: #4A4A4A">
            {{ campo().tooltip }}
          </span>
        </div>
      }
    </div>
  `,
})
export class CampoDecisionComponent {
  readonly icn = { Info };
  campo = input.required<CampoMeta>();
  value = input<number | null>(null);
  valorAnterior = input<number | null>(null);
  disabled = input(false);
  area = input<AreaDecisionV2>('COMERCIAL');
  valueChange = output<number | null>();

  colors = computed(() => AREA_COLORS[this.area()]);
  esGuarani = computed(() => this.campo().unidad === 'guarani');

  private focused = signal(false);

  displayValue = computed(() => {
    const val = this.value();
    if (val == null) return '';
    if (this.focused()) return String(val);
    return this.formatGuarani(val);
  });

  formatVal(val: number | null): string {
    if (val == null) return '—';
    const unidad = this.campo().unidad;
    if (unidad === 'guarani') return 'Gs ' + this.formatGuarani(val);
    if (unidad === 'porcentaje') return val + '%';
    if (unidad === 'personas') return val + '';
    return val.toLocaleString('es-PY') + ' uds';
  }

  deltaText = computed(() => {
    const current = this.value();
    const prev = this.valorAnterior();
    if (current == null || prev == null || prev === 0) return '';
    const diff = current - prev;
    if (diff === 0) return '';
    const pct = ((diff / Math.abs(prev)) * 100).toFixed(0);
    return (diff > 0 ? '+' : '') + pct + '%';
  });

  deltaPositive = computed(() => {
    const current = this.value();
    const prev = this.valorAnterior();
    if (current == null || prev == null) return true;
    return current >= prev;
  });

  onValueChange(val: any): void {
    const num = val === '' || val == null ? null : Number(val);
    this.valueChange.emit(isNaN(num as number) ? null : num);
  }

  onGuaraniInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    // Strip non-digit chars except minus
    const raw = input.value.replace(/[^\d-]/g, '');
    const num = raw === '' || raw === '-' ? null : Number(raw);
    if (num != null && !isNaN(num)) {
      this.valueChange.emit(num);
      // Reformat while typing: put cursor-friendly formatted value
      if (!this.focused()) {
        input.value = this.formatGuarani(num);
      }
    } else if (raw === '' || raw === '-') {
      this.valueChange.emit(null);
    }
  }

  onFocus(): void {
    this.focused.set(true);
  }

  onBlur(): void {
    this.focused.set(false);
  }

  private formatGuarani(val: number): string {
    return Math.abs(val)
      .toString()
      .replace(/\B(?=(\d{3})+(?!\d))/g, '.')
      .replace(/^/, val < 0 ? '-' : '');
  }
}

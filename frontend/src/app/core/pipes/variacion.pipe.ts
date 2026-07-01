import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'variacion', standalone: true })
export class VariacionPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    if (value == null) return '—';
    const pct = (value * 100).toFixed(1);
    if (value > 0) return `▲ +${pct}%`;
    if (value < 0) return `▼ ${pct}%`;
    return `— 0.0%`;
  }
}

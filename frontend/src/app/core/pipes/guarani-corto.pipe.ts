import { Pipe, PipeTransform } from '@angular/core';
import { GuaraniPipe } from './guarani.pipe';

@Pipe({ name: 'guaraniCorto', standalone: true })
export class GuaraniCortoPipe implements PipeTransform {
  private fullPipe = new GuaraniPipe();

  transform(value: number | null | undefined): string {
    const num = value ?? 0;
    if (Math.abs(num) >= 1_000_000) {
      const millions = num / 1_000_000;
      const formatted = millions.toFixed(1).replace('.', ',');
      const parts = formatted.split(',');
      parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, '.');
      return `Gs. ${parts.join('.')}M`;
    }
    return this.fullPipe.transform(num);
  }
}

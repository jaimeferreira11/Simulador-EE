import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'guarani', standalone: true })
export class GuaraniPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    const num = value ?? 0;
    const formatted = Math.abs(num)
      .toString()
      .replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    return num < 0 ? `Gs. -${formatted}` : `Gs. ${formatted}`;
  }
}

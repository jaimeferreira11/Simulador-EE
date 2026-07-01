import { Pipe, PipeTransform } from '@angular/core';

/**
 * Formats a number as Paraguayan Guarani (integer, dot as thousands separator).
 * Usage: {{ 500000000 | guarani }} → "500.000.000"
 */
@Pipe({ name: 'guarani', standalone: true })
export class GuaraniPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    if (value == null) return '0';
    return Math.round(value).toLocaleString('es-PY');
  }
}

import { Component, computed, input } from '@angular/core';

const ESTADO_CONFIG: Record<string, { label: string; color: string }> = {
  BORRADOR: { label: 'Borrador', color: 'bg-gray-100 text-gray-600' },
  ABIERTA_INSCRIPCION: {
    label: 'Inscripción',
    color: 'bg-blue-50 text-blue-700',
  },
  EN_CURSO: { label: 'En Curso', color: 'bg-primary text-white' },
  PAUSADA: { label: 'Pausada', color: 'bg-amber-50 text-amber-700' },
  PENDIENTE_FINALIZAR: {
    label: 'Pendiente Finalizar',
    color: 'bg-amber-100 text-amber-800',
  },
  FINALIZADA: { label: 'Finalizada', color: 'bg-accent-light text-accent' },
  ARCHIVADA: { label: 'Archivada', color: 'bg-gray-50 text-gray-400' },
  PENDIENTE: { label: 'Pendiente', color: 'bg-gray-100 text-gray-600' },
  ABIERTO_DECISIONES: {
    label: 'Decisiones Abiertas',
    color: 'bg-primary-light text-primary',
  },
  CERRADO_PROCESANDO: {
    label: 'Procesando',
    color: 'bg-amber-50 text-amber-700',
  },
  PROCESADO: { label: 'Procesado', color: 'bg-gray-100 text-gray-700' },
  ENVIADA: { label: 'Enviada', color: 'bg-primary-light text-primary' },
  PROCESADA: { label: 'Procesada', color: 'bg-gray-100 text-gray-700' },
  ACTIVO: { label: 'Activo', color: 'bg-primary-light text-primary' },
  INTERVENIDO: { label: 'Intervenido', color: 'bg-amber-50 text-amber-700' },
  QUEBRADO: { label: 'Quebrado', color: 'bg-red-50 text-red-700' },
  ELIMINADO: { label: 'Eliminado', color: 'bg-red-50 text-red-600' },
};

@Component({
  selector: 'app-estado-chip',
  standalone: true,
  template: `
    <span
      class="inline-flex items-center px-2.5 py-0.5 rounded text-[11px] font-label font-semibold tracking-wide uppercase"
      [class]="config().color"
    >
      {{ config().label }}
    </span>
  `,
})
export class EstadoChipComponent {
  estado = input.required<string>();

  config = computed(() => {
    return (
      ESTADO_CONFIG[this.estado()] ?? {
        label: this.estado(),
        color: 'bg-gray-100 text-gray-600',
      }
    );
  });
}

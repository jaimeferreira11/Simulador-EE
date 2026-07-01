import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { LucideAngularModule, TrendingUp, TrendingDown, Minus, AlertTriangle } from 'lucide-angular';
import { JugadorStore } from '../../../core/stores/jugador.store';
import { EventoApiService } from '../../../core/services/evento-api.service';
import { EventoCompetencia, Severidad } from '../../../core/models/evento.model';

@Component({
  selector: 'app-noticias',
  standalone: true,
  imports: [LucideAngularModule],
  templateUrl: './noticias.component.html',
})
export class NoticiasComponent implements OnInit {
  readonly icons = { TrendingUp, TrendingDown, Minus, AlertTriangle };

  private jugadorStore = inject(JugadorStore);
  private eventoApi = inject(EventoApiService);

  loading = signal(true);
  eventos = signal<EventoCompetencia[]>([]);
  filtroActual: 'todos' | 'moderador' | 'automaticos' = 'todos';

  competencia = this.jugadorStore.competencia;
  trimestreActual = this.jugadorStore.trimestreActual;

  competenciaNombre = computed(() => this.competencia()?.nombre ?? '');
  trimestreNumero = computed(() => this.trimestreActual()?.numero ?? 0);

  noticiasDisplay = computed(() => {
    return this.eventos().map(e => {
      const sev = e.evento_catalogo.severidad;
      return {
        titulo: e.evento_catalogo.nombre,
        descripcion: e.evento_catalogo.descripcion,
        fecha: e.disparado_at
          ? new Date(e.disparado_at).toLocaleDateString('es-PY', { day: 'numeric', month: 'long', year: 'numeric' })
          : 'Sin fecha',
        tipo: this.severidadLabel(sev),
        tipoColor: this.severidadBadgeColor(sev),
        borderColor: this.severidadBorderColor(sev),
        impactos: [{
          texto: `${this.tipoEfectoLabel(e.evento_catalogo.tipo_efecto)} ${e.evento_catalogo.magnitud_default > 0 ? '+' : ''}${(e.evento_catalogo.magnitud_default * 100).toFixed(0)}%`,
          color: e.evento_catalogo.magnitud_default > 0 ? 'text-red-600' : 'text-primary',
          icon: e.evento_catalogo.magnitud_default > 0 ? TrendingUp : TrendingDown,
        }],
      };
    });
  });

  ngOnInit(): void {
    this.initData();
  }

  private async initData(): Promise<void> {
    await this.jugadorStore.init();

    const comp = this.competencia();
    if (!comp) {
      this.loading.set(false);
      return;
    }

    this.eventoApi.listByCompetencia(comp.id).subscribe({
      next: (evts) => {
        this.eventos.set(evts);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private severidadLabel(s: Severidad): string {
    switch (s) {
      case 'GRAVE': return 'Alto Impacto';
      case 'MODERADO': return 'Moderado';
      case 'LEVE': return 'Informativo';
      case 'POSITIVO': return 'Positivo';
    }
  }

  private severidadBadgeColor(s: Severidad): string {
    switch (s) {
      case 'GRAVE': return 'bg-red-100 text-red-700';
      case 'MODERADO': return 'bg-amber-100 text-amber-700';
      case 'LEVE': return 'bg-blue-100 text-blue-700';
      case 'POSITIVO': return 'bg-green-100 text-green-700';
    }
  }

  private severidadBorderColor(s: Severidad): string {
    switch (s) {
      case 'GRAVE': return 'border-l-red-500';
      case 'MODERADO': return 'border-l-amber-400';
      case 'LEVE': return 'border-l-blue-400';
      case 'POSITIVO': return 'border-l-primary';
    }
  }

  private tipoEfectoLabel(tipo: string): string {
    const labels: Record<string, string> = {
      COSTO_LOGISTICO: 'Costos logísticos',
      COSTO_FIJO: 'Costos fijos',
      COSTO_MP: 'Costos materia prima',
      DEMANDA_TOTAL: 'Demanda total',
      TASA_INTERES: 'Tasa de interés',
      TIPO_CAMBIO: 'Tipo de cambio',
    };
    return labels[tipo] ?? tipo;
  }
}

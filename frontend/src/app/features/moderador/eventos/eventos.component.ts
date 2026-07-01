import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { DatePipe } from '@angular/common';
import { LucideAngularModule, Zap, X, TrendingUp, TrendingDown } from 'lucide-angular';
import { HotToastService } from '@ngxpert/hot-toast';
import Swal from 'sweetalert2';
import { CompetenciaApiService } from '../../../core/services/competencia-api.service';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { CatalogoApiService } from '../../../core/services/catalogo-api.service';
import { EventoApiService } from '../../../core/services/evento-api.service';
import { CompetenciaDetalle } from '../../../core/models/competencia.model';
import { EventoCatalogo, EventoCompetencia, Severidad } from '../../../core/models/evento.model';

@Component({
  selector: 'app-eventos-mod',
  standalone: true,
  imports: [FormsModule, DatePipe, LucideAngularModule],
  templateUrl: './eventos.component.html',
})
export class EventosModComponent implements OnInit {
  readonly icons = { Zap, X, TrendingUp, TrendingDown };

  private readonly tipoEfectoLabels: Record<string, string> = {
    COSTO_LOGISTICO: 'Costo Logistico',
    COSTO_FIJO: 'Costos Fijos',
    COSTO_MP: 'Costo Mat. Prima',
    DEMANDA_TOTAL: 'Demanda Total',
    TASA_INTERES: 'Tasa de Interes',
    TIPO_CAMBIO: 'Tipo de Cambio',
  };

  tipoEfectoLabel(tipo: string): string {
    return this.tipoEfectoLabels[tipo] ?? tipo;
  }

  isEventoActivo(catalogoId: number): boolean {
    return this.catalogoIdsActivos().has(catalogoId);
  }

  private route = inject(ActivatedRoute);
  private competenciaApi = inject(CompetenciaApiService);
  private competenciaStore = inject(CompetenciaStore);
  private catalogoApi = inject(CatalogoApiService);
  private eventoApi = inject(EventoApiService);
  private toast = inject(HotToastService);

  loading = signal(true);
  competencia = signal<CompetenciaDetalle | null>(null);
  eventosCatalogo = signal<EventoCatalogo[]>([]);
  eventosActivos = signal<EventoCompetencia[]>([]);
  error = signal<string | null>(null);

  // Set of catalogo IDs that are currently active (to disable in the UI)
  catalogoIdsActivos = computed(() =>
    new Set(this.eventosActivos().map(e => e.evento_catalogo?.id).filter(Boolean))
  );

  // Can only fire events when trimestre is PENDIENTE or ABIERTO_DECISIONES
  puedeDisparar = computed(() => {
    const t = this.competencia()?.trimestre_actual;
    if (!t) return false;
    return t.estado === 'PENDIENTE' || t.estado === 'ABIERTO_DECISIONES';
  });

  // Disparar dialog
  showDisparar = signal(false);
  eventoSeleccionado = signal<EventoCatalogo | null>(null);
  dispararJustificacion = '';
  dispararLoading = signal(false);

  readonly severidadMap: Record<Severidad, { label: string; class: string }> = {
    LEVE: { label: 'Leve', class: 'bg-blue-100 text-blue-700' },
    MODERADO: { label: 'Moderado', class: 'bg-amber-100 text-amber-700' },
    GRAVE: { label: 'Grave', class: 'bg-red-100 text-red-700' },
    POSITIVO: { label: 'Positivo', class: 'bg-green-100 text-green-700' },
  };

  ngOnInit(): void {
    const competenciaId = Number(this.route.snapshot.queryParamMap.get('competencia'))
      || this.competenciaStore.competenciaActiva()?.id;
    if (!competenciaId) {
      this.loading.set(false);
      return;
    }
    this.loadData(competenciaId);
  }

  private loadData(competenciaId: number): void {
    this.loading.set(true);
    this.competenciaApi.getById(competenciaId).subscribe({
      next: (competencia) => {
        this.competencia.set(competencia);
        this.competenciaStore.competenciaActiva.set(competencia);
        const trimId = competencia.trimestre_actual?.id;

        forkJoin({
          catalogo: this.catalogoApi.getEventosCatalogo(competencia.rubro_id),
          activos: this.eventoApi.listByCompetencia(competenciaId, trimId),
        }).subscribe({
          next: ({ catalogo, activos }) => {
            this.eventosCatalogo.set(catalogo);
            this.eventosActivos.set(activos);
            this.loading.set(false);
          },
          error: () => this.loading.set(false),
        });
      },
      error: () => this.loading.set(false),
    });
  }

  abrirDisparar(evento: EventoCatalogo): void {
    this.eventoSeleccionado.set(evento);
    this.dispararJustificacion = '';
    this.showDisparar.set(true);
  }

  disparar(): void {
    const c = this.competencia();
    const evt = this.eventoSeleccionado();
    if (!c || !evt || !c.trimestre_actual) return;

    this.dispararLoading.set(true);
    this.error.set(null);
    this.eventoApi.disparar(c.id, {
      trimestre_id: c.trimestre_actual.id,
      evento_catalogo_id: evt.id,
      justificacion: this.dispararJustificacion.trim(),
    }).subscribe({
      next: () => {
        this.dispararLoading.set(false);
        this.showDisparar.set(false);
        this.toast.success(`Evento "${evt.nombre}" disparado correctamente`, {
          style: { background: '#065f46', color: '#fff' },
          iconTheme: { primary: '#34d399', secondary: '#fff' },
        });
        this.loadData(c.id);
      },
      error: (err) => {
        this.dispararLoading.set(false);
        this.showDisparar.set(false);
        Swal.fire({
          title: 'Error al disparar evento',
          text: err.error?.detail ?? 'Ocurrio un error inesperado',
          icon: 'error',
          confirmButtonText: 'Entendido',
          confirmButtonColor: '#006B3F',
        });
      },
    });
  }
}

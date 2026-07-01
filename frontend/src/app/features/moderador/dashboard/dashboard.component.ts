import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { LucideAngularModule, Plus, Users, Clock, Trophy, Filter, ChevronLeft, ChevronRight } from 'lucide-angular';
import { EstadoChipComponent } from '../../../shared/components/estado-chip/estado-chip.component';
import { CompetenciaApiService } from '../../../core/services/competencia-api.service';
import { EquipoApiService } from '../../../core/services/equipo-api.service';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { CompetenciaDetalle, EstadoCompetencia, Entidad } from '../../../core/models/competencia.model';

interface CompetenciaCard {
  id: number;
  nombre: string;
  estado: string;
  entidadNombre: string | null;
  equipos: number;
  jugadores: number;
  trimestreActual: number | null;
  trimestreEstado: string | null;
  totalTrimestres: number;
  subTexto: string;
  ganador: string | null;
  listaParaFinalizar: boolean;
}

@Component({
  selector: 'app-moderador-dashboard',
  standalone: true,
  imports: [RouterLink, FormsModule, EstadoChipComponent, LucideAngularModule],
  templateUrl: './dashboard.component.html',
})
export class ModeradorDashboardComponent implements OnInit {
  readonly icons = { Plus, Users, Clock, Trophy, Filter, ChevronLeft, ChevronRight };

  private competenciaApi = inject(CompetenciaApiService);
  private equipoApi = inject(EquipoApiService);
  private competenciaStore = inject(CompetenciaStore);

  loading = signal(true);
  cards = signal<CompetenciaCard[]>([]);

  // Filters
  entidades = signal<Entidad[]>([]);
  filtroEntidad = signal<number | undefined>(undefined);
  filtroEstado = signal<EstadoCompetencia | undefined>(undefined);
  filtroAnio = signal<number | undefined>(undefined);
  aniosDisponibles = signal<number[]>([]);

  // Pagination
  currentPage = signal(0);
  pageSize = 10;
  totalElements = signal(0);
  totalPages = signal(0);

  totalCompetencias = computed(() => this.totalElements());
  enCurso = computed(() => this.cards().filter(c => c.estado === 'EN_CURSO' || c.estado === 'PENDIENTE_FINALIZAR' || c.estado === 'ABIERTA_INSCRIPCION').length);
  totalEquipos = computed(() => this.cards().reduce((sum, c) => sum + c.equipos, 0));
  totalParticipantes = computed(() => this.cards().reduce((sum, c) => sum + c.jugadores, 0));

  ngOnInit(): void {
    // Load entidades for filter dropdown
    this.competenciaApi.listEntidades().subscribe({
      next: (orgs) => this.entidades.set(orgs),
    });

    // Build available years (current year back to 5 years)
    const currentYear = new Date().getFullYear();
    this.aniosDisponibles.set(
      Array.from({ length: 5 }, (_, i) => currentYear - i)
    );

    this.loadPage();
  }

  onFilterChange(): void {
    this.currentPage.set(0);
    this.loadPage();
  }

  clearFilters(): void {
    this.filtroEntidad.set(undefined);
    this.filtroEstado.set(undefined);
    this.filtroAnio.set(undefined);
    this.currentPage.set(0);
    this.loadPage();
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages()) return;
    this.currentPage.set(page);
    this.loadPage();
  }

  private loadPage(): void {
    this.loading.set(true);
    const orgMap = new Map(this.entidades().map(o => [o.id, o.nombre]));

    this.competenciaApi.list(
      this.currentPage(),
      this.pageSize,
      this.filtroEstado(),
      this.filtroEntidad(),
      this.filtroAnio(),
    ).subscribe({
      next: (page) => {
        this.totalElements.set(page.total_elements);
        this.totalPages.set(page.total_pages);

        if (!page.content.length) {
          this.cards.set([]);
          this.loading.set(false);
          return;
        }

        // Auto-select active competencia for sidebar
        if (!this.competenciaStore.competenciaActiva()) {
          const activa = page.content.find(c => c.estado === 'EN_CURSO')
            ?? page.content.find(c => c.estado === 'PENDIENTE_FINALIZAR')
            ?? page.content.find(c => c.estado === 'ABIERTA_INSCRIPCION')
            ?? page.content[0];
          if (activa) {
            this.competenciaStore.load(activa.id);
          }
        }

        // Fetch detail per competencia
        forkJoin(page.content.map(c => this.competenciaApi.getById(c.id))).subscribe({
          next: (detalles) => {
            const equipoIds = detalles.flatMap(d => d.equipos?.map(e => e.id) ?? []);
            if (!equipoIds.length) {
              this.cards.set(detalles.map(d => this.toCard(d, {}, orgMap)));
              this.loading.set(false);
              return;
            }
            forkJoin(equipoIds.map(id => this.equipoApi.getById(id))).subscribe({
              next: (equipoDetalles) => {
                const miembrosMap: Record<number, number> = {};
                for (const ed of equipoDetalles) {
                  miembrosMap[ed.id] = ed.miembros?.length ?? 0;
                }
                this.cards.set(detalles.map(d => this.toCard(d, miembrosMap, orgMap)));
                this.loading.set(false);
              },
              error: () => {
                this.cards.set(detalles.map(d => this.toCard(d, {}, orgMap)));
                this.loading.set(false);
              },
            });
          },
          error: () => this.loading.set(false),
        });
      },
      error: () => this.loading.set(false),
    });
  }

  private toCard(d: CompetenciaDetalle, miembrosMap: Record<number, number>, orgMap: Map<number, string>): CompetenciaCard {
    const equipos = d.equipos?.length ?? 0;
    const jugadores = d.equipos?.reduce((sum, e) => sum + (miembrosMap[e.id] ?? 0), 0) ?? 0;
    let trimestreActual = d.trimestre_actual?.numero ?? null;
    const trimestreEstado = d.trimestre_actual?.estado ?? null;
    const pendiente = !d.trimestre_actual && d.estado === 'EN_CURSO'
      ? d.trimestres?.find(t => t.estado === 'PENDIENTE') ?? null
      : null;
    if (pendiente) trimestreActual = pendiente.numero;
    const finalTrimestreEstado = pendiente ? 'PENDIENTE' : (trimestreEstado ?? null);
    const ganador = d.estado === 'FINALIZADA'
      ? d.equipos?.find(e => e.posicion_final === 1)?.nombre_empresa ?? null
      : null;
    const listaParaFinalizar = d.estado === 'PENDIENTE_FINALIZAR';
    return {
      id: d.id,
      nombre: d.nombre,
      estado: d.estado,
      entidadNombre: d.entidad_id ? (orgMap.get(d.entidad_id) ?? null) : null,
      equipos,
      jugadores,
      trimestreActual,
      trimestreEstado: finalTrimestreEstado,
      totalTrimestres: d.num_trimestres,
      subTexto: this.getSubTexto(d.estado, d.trimestre_actual, pendiente),
      ganador,
      listaParaFinalizar,
    };
  }

  private getSubTexto(estado: string, trimestreActual: { estado?: string } | null, pendiente: { estado?: string } | null = null): string {
    switch (estado) {
      case 'BORRADOR': return 'Configuración pendiente';
      case 'ABIERTA_INSCRIPCION': return 'Esperando inscripciones';
      case 'EN_CURSO':
        if (trimestreActual?.estado === 'ABIERTO_DECISIONES') return 'Decisiones abiertas';
        if (trimestreActual?.estado === 'CERRADO_PROCESANDO') return 'Procesando resultados';
        if (pendiente) return 'Pendiente de apertura';
        return 'Esperando decisiones';
      case 'PENDIENTE_FINALIZAR': return 'Lista para finalizar';
      case 'PAUSADA': return 'Competencia pausada';
      case 'FINALIZADA': return '';
      case 'ARCHIVADA': return 'Archivada';
      default: return '';
    }
  }

  isActive(estado: string): boolean {
    return estado === 'EN_CURSO' || estado === 'ABIERTA_INSCRIPCION';
  }
}

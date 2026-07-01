import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { LucideAngularModule, Users, Calendar, Zap, Trophy, BarChart3, ClipboardList, Pause, Play, Check, Download } from 'lucide-angular';
import { EstadoChipComponent } from '../../../shared/components/estado-chip/estado-chip.component';
import { CompetenciaApiService } from '../../../core/services/competencia-api.service';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { CompetenciaDetalle, EstadoCompetencia } from '../../../core/models/competencia.model';

@Component({
  selector: 'app-gestionar-competencia',
  standalone: true,
  imports: [RouterLink, DatePipe, EstadoChipComponent, LucideAngularModule],
  templateUrl: './gestionar-competencia.component.html',
})
export class GestionarCompetenciaComponent implements OnInit {
  readonly icons = { Users, Calendar, Zap, Trophy, BarChart3, ClipboardList, Pause, Play, Check, Download };

  private route = inject(ActivatedRoute);
  private competenciaApi = inject(CompetenciaApiService);
  private competenciaStore = inject(CompetenciaStore);

  loading = signal(true);
  competencia = signal<CompetenciaDetalle | null>(null);
  actionLoading = signal(false);
  error = signal<string | null>(null);

  trimestreActualNum = computed(() => {
    const c = this.competencia();
    if (!c?.trimestre_actual) return 0;
    return c.trimestre_actual.numero;
  });

  totalJugadores = computed(() => {
    const c = this.competencia();
    if (!c) return 0;
    return c.equipos.reduce((sum, e) => sum + ((e as any).num_miembros ?? 0), 0);
  });

  acciones = computed(() => {
    const c = this.competencia();
    if (!c) return [];
    const id = c.id;
    return [
      { label: 'Gestión de Equipos', ruta: `/moderador/equipos`, queryParams: { competencia: id }, icono: this.icons.Users, descripcion: 'Agregar, editar o eliminar equipos' },
      { label: 'Control Trimestre', ruta: `/moderador/trimestres`, queryParams: { competencia: id }, icono: this.icons.Calendar, descripcion: 'Abrir/cerrar trimestres, procesar resultados' },
      { label: 'Eventos', ruta: `/moderador/eventos`, queryParams: { competencia: id }, icono: this.icons.Zap, descripcion: 'Disparar eventos macroeconómicos' },
      { label: 'Rankings', ruta: `/moderador/rankings`, queryParams: { competencia: id }, icono: this.icons.Trophy, descripcion: 'Ver posiciones y evolución' },
      { label: 'Resultados', ruta: `/moderador/resultados`, queryParams: { competencia: id }, icono: this.icons.BarChart3, descripcion: 'Ver resultados por equipo' },
      { label: 'Bitácora', ruta: `/moderador/bitacora`, queryParams: { competencia: id }, icono: this.icons.ClipboardList, descripcion: 'Historial de acciones' },
    ];
  });

  canAbrirInscripcion = computed(() => this.competencia()?.estado === 'BORRADOR');
  canIniciar = computed(() => this.competencia()?.estado === 'ABIERTA_INSCRIPCION');
  canPausar = computed(() => this.competencia()?.estado === 'EN_CURSO');
  canReanudar = computed(() => this.competencia()?.estado === 'PAUSADA');

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.loadCompetencia(id);
  }

  private loadCompetencia(id: number): void {
    this.loading.set(true);
    this.competenciaApi.getById(id).subscribe({
      next: (c) => {
        this.competencia.set(c);
        this.competenciaStore.competenciaActiva.set(c);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  abrirInscripcion(): void {
    this.doAction(() => this.competenciaApi.abrirInscripcion(this.competencia()!.id));
  }

  iniciar(): void {
    this.doAction(() => this.competenciaApi.iniciar(this.competencia()!.id));
  }

  pausar(): void {
    this.doAction(() => this.competenciaApi.pausar(this.competencia()!.id));
  }

  reanudar(): void {
    this.doAction(() => this.competenciaApi.reanudar(this.competencia()!.id));
  }

  private doAction(action: () => any): void {
    this.actionLoading.set(true);
    this.error.set(null);
    action().subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.loadCompetencia(this.competencia()!.id);
      },
      error: (err: any) => {
        this.actionLoading.set(false);
        this.error.set(err.error?.detail ?? 'Error al ejecutar la acción');
      },
    });
  }
}

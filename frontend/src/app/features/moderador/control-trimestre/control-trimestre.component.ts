import { Component, inject, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { forkJoin, of, Subscription } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { LucideAngularModule, Lock, Play, Check, Clock, Eye, Calendar } from 'lucide-angular';
import { HotToastService } from '@ngxpert/hot-toast';
import Swal from 'sweetalert2';
import { WebSocketService } from '../../../core/websocket/websocket.service';
import { EstadoChipComponent } from '../../../shared/components/estado-chip/estado-chip.component';
import { BotBadgeComponent } from '../../../shared/components/bot-badge/bot-badge.component';
import { CompetenciaApiService } from '../../../core/services/competencia-api.service';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { TrimestreApiService } from '../../../core/services/trimestre-api.service';
import { DecisionApiService } from '../../../core/services/decision-api.service';
import { CompetenciaDetalle } from '../../../core/models/competencia.model';
import { Trimestre } from '../../../core/models/trimestre.model';
import { Decision } from '../../../core/models/decision.model';
import { Equipo } from '../../../core/models/equipo.model';
import { BotDificultad, BotPersonalidad, BotTipo } from '../../../core/models/bot.model';

interface EquipoDecisionRow {
  equipoId: number;
  nombre: string;
  color: string;
  iniciales: string;
  estado: string;
  hora: string;
  tipo?: BotTipo;
  dificultad?: BotDificultad | null;
  personalidad?: BotPersonalidad | null;
}

interface TrimestreRow {
  trimestre: Trimestre;
  decisionesEnviadas: number;
  totalEquipos: number;
  esCurrent: boolean;
  puedeAbrir: boolean;
}

@Component({
  selector: 'app-control-trimestre',
  standalone: true,
  imports: [DatePipe, RouterLink, EstadoChipComponent, BotBadgeComponent, LucideAngularModule],
  templateUrl: './control-trimestre.component.html',
})
export class ControlTrimestreComponent implements OnInit, OnDestroy {
  readonly icons = { Lock, Play, Check, Clock, Eye, Calendar };

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private competenciaApi = inject(CompetenciaApiService);
  private competenciaStore = inject(CompetenciaStore);
  private trimestreApi = inject(TrimestreApiService);
  private decisionApi = inject(DecisionApiService);
  private wsService = inject(WebSocketService);
  private toast = inject(HotToastService);
  private wsSub?: Subscription;

  loading = signal(true);
  competencia = signal<CompetenciaDetalle | null>(null);
  trimestreActivo = signal<Trimestre | null>(null);
  decisiones = signal<Decision[]>([]);
  equiposDecisiones = signal<EquipoDecisionRow[]>([]);
  actionLoading = signal(false);
  error = signal<string | null>(null);
  trimestreRows = signal<TrimestreRow[]>([]);

  trimestres = computed(() => this.competencia()?.trimestres ?? []);

  // The trimestre to show: active one (ABIERTO/CERRADO) or next PENDIENTE
  trimestreDisplay = computed(() => {
    const activo = this.trimestreActivo();
    if (activo) return activo;
    // Find first PENDIENTE trimestre
    const trimestres = this.trimestres();
    return trimestres.find(t => t.estado === 'PENDIENTE') ?? null;
  });

  decisionesEnviadas = computed(() => {
    return this.decisiones().filter(d => d.estado === 'ENVIADA' || d.estado === 'PROCESADA').length;
  });

  totalEquipos = computed(() => this.competencia()?.equipos.length ?? 0);

  progressPct = computed(() => {
    const total = this.totalEquipos();
    if (!total) return 0;
    return (this.decisionesEnviadas() / total) * 100;
  });

  canAbrir = computed(() => this.trimestreDisplay()?.estado === 'PENDIENTE');
  canCerrar = computed(() => this.trimestreDisplay()?.estado === 'ABIERTO_DECISIONES');

  ngOnInit(): void {
    const competenciaId = Number(this.route.snapshot.queryParamMap.get('competencia'))
      || this.competenciaStore.competenciaActiva()?.id;
    if (!competenciaId) {
      this.loading.set(false);
      return;
    }
    this.loadData(competenciaId);
  }

  ngOnDestroy(): void {
    this.wsSub?.unsubscribe();
  }

  private loadData(competenciaId: number): void {
    this.loading.set(true);
    this.competenciaApi.getById(competenciaId).subscribe({
      next: (c) => {
        this.competencia.set(c);
        this.competenciaStore.competenciaActiva.set(c);
        this.trimestreActivo.set(c.trimestre_actual);

        if (!this.wsSub) {
          this.wsSub = this.wsService.messages$.subscribe((event) => {
            if (event.tipo === 'decision.recibida') {
              this.loadData(competenciaId);
            }
          });
        }

        const display = c.trimestre_actual;
        if (display && (display.estado === 'ABIERTO_DECISIONES' || display.estado === 'CERRADO_PROCESANDO')) {
          this.loadDecisiones(display.id, c.equipos);
        }

        // Load decision counts for all trimestres (for the list)
        this.loadTrimestreRows(c);
      },
      error: () => this.loading.set(false),
    });
  }

  private loadTrimestreRows(c: CompetenciaDetalle): void {
    const trims = c.trimestres;
    const total = c.equipos?.length ?? 0;

    const decisionCalls = trims.map(t =>
      t.estado === 'PENDIENTE'
        ? of([] as Decision[])
        : this.decisionApi.listByTrimestre(t.id).pipe(catchError(() => of([] as Decision[])))
    );

    if (decisionCalls.length === 0) {
      this.trimestreRows.set([]);
      this.loading.set(false);
      return;
    }

    forkJoin(decisionCalls).subscribe({
      next: (allDecisions) => {
        const currentIdx = trims.findIndex(t =>
          t.estado === 'ABIERTO_DECISIONES' || t.estado === 'CERRADO_PROCESANDO'
        );
        const lastProcessedIdx = trims.reduce((acc, t, i) =>
          t.estado === 'PROCESADO' ? i : acc, -1);

        const built: TrimestreRow[] = trims.map((t, i) => {
          const decs = allDecisions[i];
          const enviadas = decs.filter(d => d.estado === 'ENVIADA' || d.estado === 'PROCESADA').length;
          const esCurrent = i === currentIdx;
          const puedeAbrir = t.estado === 'PENDIENTE' && currentIdx === -1 && i === lastProcessedIdx + 1;
          return { trimestre: t, decisionesEnviadas: enviadas, totalEquipos: total, esCurrent, puedeAbrir };
        });
        this.trimestreRows.set(built);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private loadDecisiones(trimestreId: number, equipos: Equipo[]): void {
    this.decisionApi.listByTrimestre(trimestreId).subscribe({
      next: (decs) => {
        this.decisiones.set(decs);
        this.equiposDecisiones.set(equipos.map(e => {
          const dec = decs.find(d => d.equipo_id === e.id);
          const iniciales = e.nombre_empresa.split(' ').map(w => w[0]).join('').substring(0, 2).toUpperCase();
          return {
            equipoId: e.id,
            nombre: e.nombre_empresa,
            color: e.codigo_color,
            iniciales,
            estado: dec?.estado ?? 'SIN_CREAR',
            hora: dec?.submitted_at
              ? new Date(dec.submitted_at).toLocaleTimeString('es-PY', { hour: '2-digit', minute: '2-digit' })
              : '—',
            tipo: e.tipo,
            dificultad: e.dificultad,
            personalidad: e.personalidad,
          };
        }));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  abrirTrimestre(): void {
    const t = this.trimestreDisplay();
    if (!t) return;

    Swal.fire({
      title: `Abrir Trimestre Q${t.numero}`,
      html: `<p style="font-size:14px">Se habilitara la carga de decisiones para todos los equipos.</p>
             <p style="font-size:13px;color:#666;margin-top:8px">Los jugadores podran ingresar y enviar sus decisiones hasta que cierres el trimestre.</p>`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Abrir',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#006B3F',
    }).then((result) => {
      if (result.isConfirmed) {
        this.actionLoading.set(true);
        this.error.set(null);
        this.trimestreApi.abrir(t.id).subscribe({
          next: () => {
            this.actionLoading.set(false);
            this.toast.success(`Trimestre Q${t.numero} abierto para decisiones`, {
              style: { background: '#065f46', color: '#fff' },
              iconTheme: { primary: '#34d399', secondary: '#fff' },
            });
            this.loadData(this.competencia()!.id);
          },
          error: (err) => {
            this.actionLoading.set(false);
            this.error.set(err.error?.detail ?? 'Error al abrir trimestre');
          },
        });
      }
    });
  }

  cerrarYProcesar(): void {
    const t = this.trimestreDisplay();
    if (!t) return;

    const pendientes = this.totalEquipos() - this.decisionesEnviadas();

    Swal.fire({
      title: `Cerrar y Procesar Q${t.numero}`,
      html: pendientes > 0
        ? `<p style="font-size:14px"><strong>${pendientes} equipo(s)</strong> aún no enviaron sus decisiones. Se usarán valores por defecto.</p>
           <p style="font-size:13px;color:#666;margin-top:8px">Esta acción no se puede deshacer.</p>`
        : `<p style="font-size:14px">Todos los equipos enviaron sus decisiones. Se procederá a calcular los resultados.</p>`,
      icon: pendientes > 0 ? 'warning' : 'question',
      showCancelButton: true,
      confirmButtonText: 'Cerrar y Procesar',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#006B3F',
    }).then((result) => {
      if (result.isConfirmed) {
        this.actionLoading.set(true);
        this.error.set(null);
        this.trimestreApi.cerrar(t.id).subscribe({
          next: () => {
            this.actionLoading.set(false);
            const esUltimo = t.numero === this.competencia()!.num_trimestres;
            if (esUltimo) {
              Swal.fire({
                icon: 'info',
                title: `Q${t.numero} procesado`,
                html: `<p style="font-size:14px">Cerraste el último trimestre de la competencia.</p>
                       <p style="font-size:14px;margin-top:8px">Ahora podés <strong>finalizar la competencia</strong> para calcular el ganador y notificar a los equipos.</p>`,
                confirmButtonText: 'Ir al Dashboard',
                confirmButtonColor: '#006B3F',
              }).then(() => {
                this.router.navigate(['/moderador/competencia'], {
                  queryParams: { competencia: this.competencia()!.id },
                });
              });
            } else {
              Swal.fire({ icon: 'success', title: `Q${t.numero} procesado`, timer: 2000, showConfirmButton: false });
              this.router.navigate(['/moderador/competencia'], {
                queryParams: { competencia: this.competencia()!.id },
              });
            }
          },
          error: (err) => {
            this.actionLoading.set(false);
            this.error.set(err.error?.detail ?? 'Error al cerrar trimestre');
          },
        });
      }
    });
  }

  abrirTrimestreRow(row: TrimestreRow): void {
    const t = row.trimestre;
    Swal.fire({
      title: `Abrir Trimestre Q${t.numero}`,
      html: `<p style="font-size:14px">Se habilitará la carga de decisiones para todos los equipos.</p>`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Abrir',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#006B3F',
    }).then((result) => {
      if (result.isConfirmed) {
        this.actionLoading.set(true);
        this.trimestreApi.abrir(t.id).subscribe({
          next: () => {
            this.actionLoading.set(false);
            Swal.fire({ icon: 'success', title: `Q${t.numero} abierto`, timer: 2000, showConfirmButton: false });
            this.loadData(this.competencia()!.id);
          },
          error: (err) => {
            this.actionLoading.set(false);
            Swal.fire({ icon: 'error', title: 'Error', text: err.error?.detail ?? 'Error al abrir trimestre', confirmButtonColor: '#006B3F' });
          },
        });
      }
    });
  }

  cerrarTrimestreRow(row: TrimestreRow): void {
    const t = row.trimestre;
    const pendientes = row.totalEquipos - row.decisionesEnviadas;
    Swal.fire({
      title: `Cerrar y Procesar Q${t.numero}`,
      html: pendientes > 0
        ? `<p style="font-size:14px"><strong>${pendientes} equipo(s)</strong> aún no enviaron sus decisiones. Se usarán valores por defecto.</p>
           <p style="font-size:13px;color:#666;margin-top:8px">Esta acción no se puede deshacer.</p>`
        : `<p style="font-size:14px">Todos los equipos enviaron sus decisiones. Se procederá a calcular los resultados.</p>`,
      icon: pendientes > 0 ? 'warning' : 'question',
      showCancelButton: true,
      confirmButtonText: 'Cerrar y Procesar',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#006B3F',
    }).then((result) => {
      if (result.isConfirmed) {
        this.actionLoading.set(true);
        this.trimestreApi.cerrar(t.id).subscribe({
          next: () => {
            this.actionLoading.set(false);
            const esUltimo = t.numero === this.competencia()!.num_trimestres;
            if (esUltimo) {
              Swal.fire({
                icon: 'info',
                title: `Q${t.numero} procesado`,
                html: `<p style="font-size:14px">Cerraste el último trimestre de la competencia.</p>
                       <p style="font-size:14px;margin-top:8px">Ahora podés <strong>finalizar la competencia</strong> para calcular el ganador y notificar a los equipos.</p>`,
                confirmButtonText: 'Ir al Dashboard',
                confirmButtonColor: '#006B3F',
              }).then(() => {
                this.router.navigate(['/moderador/competencia'], {
                  queryParams: { competencia: this.competencia()!.id },
                });
              });
            } else {
              Swal.fire({ icon: 'success', title: `Q${t.numero} procesado`, timer: 2000, showConfirmButton: false });
              this.router.navigate(['/moderador/competencia'], {
                queryParams: { competencia: this.competencia()!.id },
              });
            }
          },
          error: (err) => {
            this.actionLoading.set(false);
            Swal.fire({ icon: 'error', title: 'Error', text: err.error?.detail ?? 'Error al cerrar trimestre', confirmButtonColor: '#006B3F' });
          },
        });
      }
    });
  }

  verDetalle(trimestreId: number): void {
    this.router.navigate(['/moderador/trimestres', trimestreId]);
  }
}

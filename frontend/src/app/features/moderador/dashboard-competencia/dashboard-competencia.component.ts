import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HotToastService } from '@ngxpert/hot-toast';
import {
  AlertTriangle,
  Check,
  ChevronDown,
  ChevronUp,
  Clock,
  LucideAngularModule,
  Pause,
  Pencil,
  Play,
  Rocket,
  Target,
  TrendingDown,
  TrendingUp,
  Trophy,
  Users,
  Zap,
} from 'lucide-angular';
import { forkJoin } from 'rxjs';
import Swal from 'sweetalert2';
import { CompetenciaDetalle } from '../../../core/models/competencia.model';
import { EventoAutomatico, EventoCompetencia } from '../../../core/models/evento.model';
import { RankingItem } from '../../../core/models/resultado.model';
import { CompetenciaApiService } from '../../../core/services/competencia-api.service';
import { DecisionApiService } from '../../../core/services/decision-api.service';
import { EquipoApiService } from '../../../core/services/equipo-api.service';
import { EventoApiService } from '../../../core/services/evento-api.service';
import { ResultadoApiService } from '../../../core/services/resultado-api.service';
import { TrimestreApiService } from '../../../core/services/trimestre-api.service';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { EstadoChipComponent } from '../../../shared/components/estado-chip/estado-chip.component';
import { BotBadgeComponent } from '../../../shared/components/bot-badge/bot-badge.component';
import { ResetDemoButtonComponent } from '../../../shared/components/reset-demo-button/reset-demo-button.component';
import { BotDificultad, BotPersonalidad, BotTipo } from '../../../core/models/bot.model';
import { Equipo } from '../../../core/models/equipo.model';
import { GuaraniCortoPipe } from '../../../core/pipes/guarani-corto.pipe';

interface DecisionStatus {
  equipoId: number;
  nombre: string;
  color: string;
  estado: string; // BORRADOR | ENVIADA | PROCESADA | SIN_CREAR
  tipo?: BotTipo;
  dificultad?: BotDificultad | null;
  personalidad?: BotPersonalidad | null;
}

@Component({
  selector: 'app-dashboard-competencia-mod',
  standalone: true,
  imports: [
    RouterLink,
    LucideAngularModule,
    EstadoChipComponent,
    BotBadgeComponent,
    ResetDemoButtonComponent,
    GuaraniCortoPipe,
  ],
  templateUrl: './dashboard-competencia.component.html',
})
export class DashboardCompetenciaModComponent implements OnInit {
  readonly icons = {
    Clock,
    Users,
    TrendingUp,
    TrendingDown,
    Zap,
    Play,
    Pause,
    Check,
    AlertTriangle,
    Target,
    Pencil,
    Rocket,
    Trophy,
    ChevronDown,
    ChevronUp,
  };

  private route = inject(ActivatedRoute);
  private competenciaApi = inject(CompetenciaApiService);
  private competenciaStore = inject(CompetenciaStore);
  private resultadoApi = inject(ResultadoApiService);
  private eventoApi = inject(EventoApiService);
  private decisionApi = inject(DecisionApiService);
  private equipoApi = inject(EquipoApiService);
  private trimestreApi = inject(TrimestreApiService);
  private router = inject(Router);
  private toast = inject(HotToastService);

  loading = signal(true);
  competencia = signal<CompetenciaDetalle | null>(null);
  ranking = signal<RankingItem[]>([]);
  eventos = signal<EventoCompetencia[]>([]);
  eventosAuto = signal<EventoAutomatico[]>([]);
  eventosAutoExpanded = signal(false);
  decisiones = signal<DecisionStatus[]>([]);
  totalJugadores = signal(0);
  actionLoading = signal(false);
  error = signal<string | null>(null);

  trimestreActual = computed(() => this.competencia()?.trimestre_actual ?? null);
  trimestreNumero = computed(() => this.trimestreActual()?.numero ?? 0);

  /** Mapa equipoId → Equipo, para obtener metadatos (tipo/bot) en listados que solo reciben id. */
  equipoMap = computed<Map<number, Equipo>>(() => {
    const eqs = this.competencia()?.equipos ?? [];
    return new Map(eqs.map((e) => [e.id, e]));
  });

  equipoById(id: number): Equipo | undefined {
    return this.equipoMap().get(id);
  }

  trimestresTimeline = computed(() => {
    const c = this.competencia();
    if (!c) return [];
    const actual = c.trimestre_actual;
    return c.trimestres
      .filter((t) => t.numero > 0)
      .map((t) => ({
        id: t.id,
        num: `Q${t.numero}`,
        estado:
          t.estado === 'PROCESADO'
            ? ('completado' as const)
            : actual && t.id === actual.id
              ? ('actual' as const)
              : ('pendiente' as const),
      }));
  });

  decisionesEnviadas = computed(
    () =>
      this.decisiones().filter((d) => d.estado === 'ENVIADA' || d.estado === 'PROCESADA').length,
  );
  totalEquipos = computed(() => this.competencia()?.equipos?.length ?? 0);

  /** Texto indicando la siguiente acción del moderador sobre el trimestre activo */
  accionSiguiente = computed(() => {
    const c = this.competencia();
    if (!c || c.estado !== 'EN_CURSO') return null;
    const tri = c.trimestre_actual;
    if (!tri) return null;
    if (tri.estado === 'ABIERTO_DECISIONES') {
      const enviadas = this.decisionesEnviadas();
      const total = this.totalEquipos();
      if (total > 0 && enviadas >= total) {
        return { texto: 'Listo para cerrar', color: 'text-primary' };
      }
      return { texto: `Esperando decisiones (${enviadas}/${total})`, color: 'text-accent' };
    }
    if (tri.estado === 'CERRADO_PROCESANDO') {
      return { texto: 'Procesando...', color: 'text-amber-600' };
    }
    return null;
  });

  canIniciar = computed(() => {
    const estado = this.competencia()?.estado;
    return estado === 'BORRADOR' || estado === 'ABIERTA_INSCRIPCION';
  });
  canPausar = computed(() => this.competencia()?.estado === 'EN_CURSO');
  canReanudar = computed(() => this.competencia()?.estado === 'PAUSADA');

  /** Competition in PENDIENTE_FINALIZAR → ready to finalize */
  canFinalizar = computed(() => {
    return this.competencia()?.estado === 'PENDIENTE_FINALIZAR';
  });

  // First PENDIENTE trimestre when there's no active one (competition is EN_CURSO but no trimestre opened yet)
  trimestrePendiente = computed(() => {
    const c = this.competencia();
    if (!c || c.estado !== 'EN_CURSO') return null;
    if (c.trimestre_actual) return null; // there's already an active trimestre
    return c.trimestres.find((t) => t.estado === 'PENDIENTE') ?? null;
  });

  ngOnInit(): void {
    const compId =
      Number(this.route.snapshot.queryParamMap.get('competencia')) ||
      this.competenciaStore.competenciaActiva()?.id;
    if (!compId) {
      this.loading.set(false);
      return;
    }
    this.loadAll(compId);
  }

  private loadAll(compId: number): void {
    this.loading.set(true);
    this.competenciaApi.getById(compId).subscribe({
      next: (c) => {
        this.competencia.set(c);
        this.competenciaStore.competenciaActiva.set(c);
        this.loadExtras(c);
      },
      error: () => this.loading.set(false),
    });
  }

  private loadExtras(c: CompetenciaDetalle): void {
    // Count jugadores
    if (c.equipos.length) {
      forkJoin(c.equipos.map((e) => this.equipoApi.getById(e.id))).subscribe({
        next: (detalles) => {
          this.totalJugadores.set(detalles.reduce((sum, d) => sum + (d.miembros?.length ?? 0), 0));
        },
      });
    }

    // Ranking from last processed trimestre
    this.resultadoApi.getRanking(c.id).subscribe({
      next: (items) => this.ranking.set(items),
    });

    // Active events
    this.eventoApi.listByCompetencia(c.id).subscribe({
      next: (evts) => this.eventos.set(evts),
    });

    // Auto events
    this.eventoApi.listAutomaticos(c.id).subscribe({
      next: (evts) => this.eventosAuto.set(evts),
      error: () => this.eventosAuto.set([]),
    });

    // Decision status per team for current trimestre
    const trimActual = c.trimestre_actual;
    if (trimActual && trimActual.estado === 'ABIERTO_DECISIONES' && c.equipos.length) {
      this.decisionApi.listByTrimestre(trimActual.id).subscribe({
        next: (allDecisions) => {
          const decMap = new Map(allDecisions.map((d) => [d.equipo_id, d.estado]));
          this.decisiones.set(
            c.equipos.map((eq) => ({
              equipoId: eq.id,
              nombre: eq.nombre_empresa,
              color: eq.codigo_color,
              estado: decMap.get(eq.id) ?? 'SIN_CREAR',
              tipo: eq.tipo,
              dificultad: eq.dificultad,
              personalidad: eq.personalidad,
            })),
          );
          this.loading.set(false);
        },
        error: () => {
          this.decisiones.set(
            c.equipos.map((eq) => ({
              equipoId: eq.id,
              nombre: eq.nombre_empresa,
              color: eq.codigo_color,
              estado: 'SIN_CREAR',
              tipo: eq.tipo,
              dificultad: eq.dificultad,
              personalidad: eq.personalidad,
            })),
          );
          this.loading.set(false);
        },
      });
    } else {
      this.loading.set(false);
    }
  }

  iniciarCompetencia(): void {
    const c = this.competencia();
    if (!c) return;

    // Frontend validations
    const equipos = c.equipos ?? [];
    if (equipos.length < 2) {
      Swal.fire({
        title: 'No se puede iniciar',
        text: `Se necesitan al menos 2 equipos. Actualmente hay ${equipos.length}.`,
        icon: 'warning',
        confirmButtonText: 'Entendido',
        confirmButtonColor: '#006B3F',
      });
      return;
    }

    // Check each team has at least 1 member (use totalJugadores as proxy, or check equipos)
    // We need to validate per-team, so we load details
    this.actionLoading.set(true);
    forkJoin(equipos.map((e) => this.equipoApi.getById(e.id))).subscribe({
      next: (detalles) => {
        const sinMiembros = detalles.filter((d) => !d.miembros?.length);
        if (sinMiembros.length) {
          this.actionLoading.set(false);
          const nombres = sinMiembros.map((d) => d.nombre_empresa).join(', ');
          Swal.fire({
            title: 'Equipos sin integrantes',
            text: `Los siguientes equipos no tienen miembros: ${nombres}`,
            icon: 'warning',
            confirmButtonText: 'Entendido',
            confirmButtonColor: '#006B3F',
          });
          return;
        }

        this.actionLoading.set(false);
        Swal.fire({
          title: 'Iniciar Competencia',
          html: `<p style="font-size:14px">Se crearan los ${c.num_trimestres} trimestres y la competencia pasara a <strong>EN CURSO</strong>.</p>
                 <p style="font-size:13px;color:#666;margin-top:8px">Los equipos sin capitan tendran uno asignado automaticamente.</p>`,
          icon: 'question',
          showCancelButton: true,
          confirmButtonText: 'Iniciar',
          cancelButtonText: 'Cancelar',
          confirmButtonColor: '#006B3F',
        }).then((result) => {
          if (result.isConfirmed) {
            this.actionLoading.set(true);
            this.competenciaApi.iniciar(c.id).subscribe({
              next: () => {
                this.actionLoading.set(false);
                // After initiating, ask if they want to open Q1
                Swal.fire({
                  title: 'Competencia iniciada',
                  html: `<p style="font-size:14px">La competencia ya se encuentra <strong>EN CURSO</strong>.</p>
                         <p style="font-size:14px;margin-top:12px">¿Deseas abrir el <strong>Trimestre Q1</strong> ahora?</p>
                         <p style="font-size:13px;color:#666;margin-top:8px">Los equipos podran ingresar y enviar sus decisiones inmediatamente.</p>`,
                  icon: 'success',
                  showCancelButton: true,
                  confirmButtonText: 'Abrir Q1',
                  cancelButtonText: 'Mas tarde',
                  confirmButtonColor: '#006B3F',
                }).then((abrirResult) => {
                  if (abrirResult.isConfirmed) {
                    // Reload competencia to get the trimestres, then open Q1
                    this.competenciaApi.getById(c.id).subscribe({
                      next: (updated) => {
                        const q1 = updated.trimestres.find(
                          (t) => t.numero === 1 && t.estado === 'PENDIENTE',
                        );
                        if (q1) {
                          this.actionLoading.set(true);
                          this.trimestreApi.abrir(q1.id).subscribe({
                            next: () => {
                              this.actionLoading.set(false);
                              this.toast.success('Trimestre Q1 abierto para decisiones', {
                                style: { background: '#065f46', color: '#fff' },
                                iconTheme: { primary: '#34d399', secondary: '#fff' },
                              });
                              this.router.navigate(['/moderador/competencia'], {
                                queryParams: { competencia: c.id },
                              });
                            },
                            error: () => {
                              this.actionLoading.set(false);
                              this.router.navigate(['/moderador/competencia'], {
                                queryParams: { competencia: c.id },
                              });
                            },
                          });
                        } else {
                          this.router.navigate(['/moderador/competencia'], {
                            queryParams: { competencia: c.id },
                          });
                        }
                      },
                      error: () => {
                        this.router.navigate(['/moderador/competencia'], {
                          queryParams: { competencia: c.id },
                        });
                      },
                    });
                  } else {
                    this.router.navigate(['/moderador/competencia'], {
                      queryParams: { competencia: c.id },
                    });
                  }
                });
              },
              error: (err) => {
                this.actionLoading.set(false);
                Swal.fire({
                  title: 'Error al iniciar',
                  text: err.error?.detail ?? 'Ocurrio un error inesperado',
                  icon: 'error',
                  confirmButtonText: 'Entendido',
                  confirmButtonColor: '#006B3F',
                });
              },
            });
          }
        });
      },
      error: () => {
        this.actionLoading.set(false);
      },
    });
  }

  pausar(): void {
    this.doAction(() => this.competenciaApi.pausar(this.competencia()!.id));
  }

  reanudar(): void {
    this.doAction(() => this.competenciaApi.reanudar(this.competencia()!.id));
  }

  abrirTrimestre(): void {
    const t = this.trimestrePendiente();
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
            this.loadAll(this.competencia()!.id);
          },
          error: (err) => {
            this.actionLoading.set(false);
            this.error.set(err.error?.detail ?? 'Error al abrir trimestre');
          },
        });
      }
    });
  }

  finalizarCompetencia(): void {
    const c = this.competencia();
    if (!c) return;

    Swal.fire({
      title: 'Finalizar Competencia',
      html: `<p style="font-size:14px">Se calculará el <strong>ganador</strong> y se notificará a todos los equipos para que vean los resultados finales.</p>
             <p style="font-size:13px;color:#666;margin-top:8px">Esta acción no se puede deshacer.</p>`,
      icon: 'question',
      showCancelButton: true,
      confirmButtonText: 'Finalizar',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#006B3F',
    }).then((result) => {
      if (result.isConfirmed) {
        this.actionLoading.set(true);
        this.error.set(null);
        this.competenciaApi.finalizar(c.id).subscribe({
          next: () => {
            this.actionLoading.set(false);
            Swal.fire({
              icon: 'success',
              title: 'Competencia Finalizada',
              html: `<p style="font-size:14px">La competencia ha sido finalizada exitosamente.</p>
                     <p style="font-size:14px;margin-top:8px">Los equipos fueron notificados y ya pueden ver los resultados.</p>`,
              confirmButtonText: 'Ver Ranking Final',
              confirmButtonColor: '#006B3F',
            }).then(() => {
              this.router.navigate(['/moderador/rankings'], {
                queryParams: { competencia: c.id },
              });
            });
          },
          error: (err) => {
            this.actionLoading.set(false);
            Swal.fire({
              title: 'Error al finalizar',
              text: err.error?.detail ?? 'Ocurrió un error inesperado',
              icon: 'error',
              confirmButtonText: 'Entendido',
              confirmButtonColor: '#006B3F',
            });
          },
        });
      }
    });
  }

  private doAction(action: () => any): void {
    this.actionLoading.set(true);
    this.error.set(null);
    action().subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.loadAll(this.competencia()!.id);
      },
      error: (err: any) => {
        this.actionLoading.set(false);
        this.error.set(err.error?.detail ?? 'Error al ejecutar la acción');
      },
    });
  }
}

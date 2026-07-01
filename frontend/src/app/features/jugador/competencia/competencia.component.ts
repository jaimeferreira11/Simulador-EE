import { Component, inject, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  LucideAngularModule,
  Clock,
  AlertTriangle,
  Pencil,
  Eye,
  TrendingUp,
  TrendingDown,
  Target,
  Check,
  Fuel,
  Percent,
  Trophy,
  Crown,
  ArrowRight,
  Info,
  ListChecks,
  Building2,
} from 'lucide-angular';
import confetti from 'canvas-confetti';
import { Subscription } from 'rxjs';
import { HotToastService } from '@ngxpert/hot-toast';
import { JugadorStore } from '../../../core/stores/jugador.store';
import { AuthStore } from '../../../core/stores/auth.store';
import { WebSocketService } from '../../../core/websocket/websocket.service';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { ResultadoApiService } from '../../../core/services/resultado-api.service';
import { EventoApiService } from '../../../core/services/evento-api.service';
import { DecisionApiService } from '../../../core/services/decision-api.service';
import { RankingItem } from '../../../core/models/resultado.model';
import { EventoAutomatico, EventoCompetencia } from '../../../core/models/evento.model';
import { Decision } from '../../../core/models/decision.model';
import { GuaraniCortoPipe } from '../../../core/pipes/guarani-corto.pipe';

@Component({
  selector: 'app-competencia-jugador',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, GuaraniCortoPipe],
  templateUrl: './competencia.component.html',
})
export class CompetenciaJugadorComponent implements OnInit, OnDestroy {
  readonly icons = {
    Clock,
    AlertTriangle,
    Pencil,
    Eye,
    TrendingUp,
    TrendingDown,
    Target,
    Check,
    Fuel,
    Percent,
    Trophy,
    Crown,
    ArrowRight,
    Info,
    ListChecks,
    Building2,
  };

  // Decisiones que toma cada equipo por trimestre (catálogo de campos visibles; dividendos omitido).
  readonly DECISIONES_POR_TRIMESTRE = 9;

  private jugadorStore = inject(JugadorStore);
  private authStore = inject(AuthStore);
  private wsService = inject(WebSocketService);
  private competenciaStore = inject(CompetenciaStore);
  private toast = inject(HotToastService);
  private resultadoApi = inject(ResultadoApiService);
  private eventoApi = inject(EventoApiService);
  private decisionApi = inject(DecisionApiService);
  private wsSub?: Subscription;

  loading = this.jugadorStore.loading;
  competencia = this.jugadorStore.competencia;
  equipo = this.jugadorStore.equipo;
  trimestres = this.jugadorStore.trimestres;
  trimestreActual = this.jugadorStore.trimestreActual;
  nombreEquipo = this.jugadorStore.nombreEquipo;

  ranking = signal<RankingItem[]>([]);
  eventos = signal<EventoCompetencia[]>([]);
  eventosAuto = signal<EventoAutomatico[]>([]);
  decision = signal<Decision | null>(null);

  // Computed: trimestres for the progress bar
  trimestresTimeline = computed(() => {
    const all = this.trimestres();
    const actual = this.trimestreActual();
    const finalizada = this.competenciaFinalizada();
    return all.map((t) => ({
      num: `Q${t.numero}`,
      trimestreId: t.id,
      estado:
        finalizada || t.estado === 'PROCESADO'
          ? ('completado' as const)
          : t.id === actual?.id
            ? ('actual' as const)
            : ('pendiente' as const),
      label:
        !finalizada && t.id === actual?.id && t.estado === 'ABIERTO_DECISIONES'
          ? 'Abierto'
          : undefined,
    }));
  });

  // Computed: current trimestre number
  trimestreNumero = computed(() => this.trimestreActual()?.numero ?? 0);
  totalTrimestres = computed(() => this.trimestres().length);

  // ── Información general del concurso ──
  rubroNombre = computed(() => this.competencia()?.rubro?.nombre ?? null);
  numEquipos = computed(() => this.competencia()?.equipos?.length ?? 0);
  numTrimestresConcurso = computed(
    () => this.competencia()?.num_trimestres ?? this.totalTrimestres(),
  );
  totalDecisiones = computed(() => this.DECISIONES_POR_TRIMESTRE * this.numTrimestresConcurso());

  // Computed: cierre countdown (null when finalized)
  cierreEn = computed(() => {
    if (this.competenciaFinalizada()) return null;
    const t = this.trimestreActual();
    if (!t?.cierre_at) return null;
    const diff = new Date(t.cierre_at).getTime() - Date.now();
    if (diff <= 0) return null;
    const days = Math.floor(diff / 86400000);
    const hours = Math.floor((diff % 86400000) / 3600000);
    return `${days} días, ${hours} horas`;
  });

  // Stats from the last processed trimestre ranking for our equipo
  miRanking = computed(() => {
    const eq = this.equipo();
    if (!eq) return null;
    return this.ranking().find((r) => r.equipo_id === eq.id) ?? null;
  });

  // Decision completeness
  decisionEstado = computed(() => {
    const d = this.decision();
    if (!d)
      return { completadas: 0, total: this.DECISIONES_POR_TRIMESTRE, estado: 'SIN_CREAR' as const };

    // Count how many fields have been filled (dividendos omitido del flujo del jugador)
    const fields = [
      d.precio_venta,
      d.produccion_planificada,
      d.inversion_capacidad,
      d.inversion_marketing,
      d.contrataciones_netas,
      d.aumento_salarial_pct,
      d.inversion_capacitacion,
      d.inversion_id,
      d.prestamo_solicitado,
    ];
    const completadas = fields.filter((v) => v != null && v !== 0).length;
    return { completadas, total: fields.length, estado: d.estado };
  });

  // Ranking list for mini ranking section, with highlight for our team
  rankingDisplay = computed(() => {
    const eq = this.equipo();
    return this.ranking().map((r) => ({
      pos: r.posicion,
      nombre: r.nombre_empresa,
      // Criterio del ranking = utilidad acumulada (el PIP es índice secundario).
      util: r.utilidad_acumulada,
      highlight: eq ? r.equipo_id === eq.id : false,
    }));
  });

  // Eventos for the news section (latest 2 from current trimestre)
  eventosDisplay = computed(() => {
    return this.eventos()
      .slice(0, 2)
      .map((e) => ({
        titulo: e.evento_catalogo.nombre,
        descripcion: e.evento_catalogo.descripcion,
        severidad: e.evento_catalogo.severidad,
      }));
  });

  // Auto events for this team
  eventosAutoDisplay = computed(() => {
    const eq = this.equipo();
    if (!eq) return [];
    return this.eventosAuto()
      .filter((e) => e.equipo_id === eq.id)
      .map((e) => ({
        titulo: e.regla_nombre,
        descripcion: e.regla_descripcion,
        efecto_tipo: e.efecto_tipo,
        efecto_valor: e.efecto_valor,
        trimestre_inicio: e.trimestre_efecto_inicio,
        trimestre_fin: e.trimestre_efecto_fin,
        esNegativo: e.efecto_valor < 0,
      }));
  });

  // Finalized competition detection
  competenciaFinalizada = computed(() => {
    const estado = this.competencia()?.estado;
    return estado === 'FINALIZADA' || estado === 'PENDIENTE_FINALIZAR';
  });

  esGanador = computed(() => {
    const eq = this.equipo();
    return eq?.posicion_final === 1;
  });

  posicionFinal = computed(() => this.equipo()?.posicion_final ?? null);

  ngOnInit(): void {
    this.initData();
  }

  private async initData(): Promise<void> {
    await this.jugadorStore.init();

    const comp = this.competencia();
    if (!comp) return;

    this.wsSub = this.wsService.messages$.subscribe((event) => {
      if (event.tipo === 'trimestre.procesado') {
        this.toast.success('Resultados del trimestre listos');
        this.competenciaStore.reload().then(() => this.loadExtras());
      }
    });

    this.loadExtras();

    // Trigger confetti for winners (only once per session per competencia)
    const confettiKey = `confetti_shown_${comp.id}`;
    if (this.competenciaFinalizada() && this.esGanador() && !sessionStorage.getItem(confettiKey)) {
      sessionStorage.setItem(confettiKey, '1');
      setTimeout(() => this.fireConfetti(), 500);
    }
  }

  ngOnDestroy(): void {
    this.wsSub?.unsubscribe();
  }

  private fireConfetti(): void {
    const duration = 3000;
    const end = Date.now() + duration;

    const frame = () => {
      confetti({
        particleCount: 3,
        angle: 60,
        spread: 55,
        origin: { x: 0, y: 0.7 },
        colors: ['#006B3F', '#F47920', '#FFD700'],
      });
      confetti({
        particleCount: 3,
        angle: 120,
        spread: 55,
        origin: { x: 1, y: 0.7 },
        colors: ['#006B3F', '#F47920', '#FFD700'],
      });
      if (Date.now() < end) {
        requestAnimationFrame(frame);
      }
    };
    frame();
  }

  private loadExtras(): void {
    const comp = this.competencia();
    const eq = this.equipo();
    const trimActual = this.trimestreActual();
    if (!comp) return;

    // Always load ranking and events
    this.resultadoApi.getRanking(comp.id).subscribe({
      next: (items) => this.ranking.set(items),
    });

    this.eventoApi.listByCompetencia(comp.id).subscribe({
      next: (evts) => this.eventos.set(evts),
    });

    this.eventoApi.listAutomaticos(comp.id).subscribe({
      next: (evts) => this.eventosAuto.set(evts),
      error: () => this.eventosAuto.set([]),
    });

    // Only load decision when competition is active and trimestre is open
    if (
      !this.competenciaFinalizada() &&
      trimActual &&
      eq &&
      trimActual.estado === 'ABIERTO_DECISIONES'
    ) {
      this.decisionApi.get(eq.id, trimActual.id).subscribe({
        next: (d) => this.decision.set(d),
        error: () => this.decision.set(null),
      });
    }
  }
}

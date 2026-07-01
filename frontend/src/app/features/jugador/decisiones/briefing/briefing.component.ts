import { Component, input, output, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import {
  LucideAngularModule,
  Clock,
  Play,
  Wallet,
  PieChart,
  Trophy,
  Medal,
  BookOpen,
  ArrowRight,
  Building2,
} from 'lucide-angular';
import { ContextoDecision } from '../../../../core/models/contexto-decision.model';
import { Decision } from '../../../../core/models/decision.model';
import { EventoCardComponent } from '../shared/evento-card.component';
import { KpiCardComponent } from '../shared/kpi-card.component';
import { GuaraniPipe } from '../../../../core/pipes/guarani.pipe';
import { JugadorStore } from '../../../../core/stores/jugador.store';

@Component({
  selector: 'app-briefing',
  standalone: true,
  imports: [DecimalPipe, LucideAngularModule, EventoCardComponent, KpiCardComponent, GuaraniPipe],
  templateUrl: './briefing.component.html',
})
export class BriefingComponent {
  private jugadorStore = inject(JugadorStore);

  readonly icons = {
    Clock,
    Play,
    Wallet,
    PieChart,
    Trophy,
    Medal,
    BookOpen,
    ArrowRight,
    Building2,
  };
  contexto = input.required<ContextoDecision>();
  decision = input<Decision | null>(null);
  soloLectura = input(false);
  comenzar = output();

  get snap() {
    return this.contexto().snapshot_inicio;
  }
  get mercado() {
    return this.contexto().mercado;
  }
  get eventos() {
    return this.contexto().eventos_activos;
  }
  get ranking() {
    return this.contexto().ranking_anterior;
  }
  get trimestre() {
    return this.contexto().trimestre;
  }

  get competenciaNombre(): string {
    return this.jugadorStore.competencia()?.nombre ?? 'Desafío Empresarial 2026';
  }

  get equipoNombre(): string {
    return this.jugadorStore.equipo()?.nombre_empresa ?? 'Equipo Alpha Corp';
  }

  /** Cantidad de empresas (equipos) que participan en la competencia. */
  get totalEmpresas(): number {
    return this.jugadorStore.competencia()?.equipos?.length ?? this.ranking.length;
  }

  get miEquipoId(): number {
    return this.jugadorStore.equipo()?.id ?? 0;
  }

  get miPosicion(): number {
    const miRank = this.ranking.find((r) => r.equipo_id === this.miEquipoId);
    return miRank?.posicion ?? 0;
  }

  get miRankingItem() {
    return this.ranking.find((r) => r.equipo_id === this.miEquipoId);
  }

  get cajaDelta(): string {
    if (this.trimestre.numero <= 1) return '';
    const caja = this.snap.caja;
    if (caja >= 0) return '+';
    return '−';
  }

  get shareDelta(): string {
    const item = this.miRankingItem;
    if (!item) return '';
    const share = item.share * 100;
    return share >= 25 ? '↑' : '↓';
  }

  get pipDelta(): string {
    if (this.trimestre.numero <= 1) return '';
    const pip = this.snap.pip;
    return pip >= 50 ? '↑' : '↓';
  }

  get posicionDelta(): string {
    const pos = this.miPosicion;
    if (!pos) return '';
    const total = this.ranking.length;
    if (total === 0) return '';
    return pos <= Math.ceil(total / 2) ? '↑' : '↓';
  }

  get resumenEjecutivo(): string {
    const ctx = this.contexto();
    const num = ctx.trimestre.numero;

    if (num <= 1) {
      return `Bienvenido al primer trimestre de la competencia. Revisa los indicadores clave y los eventos activos antes de tomar tus decisiones.`;
    }

    // Resumen basico con datos reales — proximamente generado por IA
    const pip = this.snap.pip.toFixed(1);
    const caja = (this.snap.caja / 1_000_000).toFixed(1);
    let texto = `Tu empresa cerro el Q${num - 1} con un PIP de ${pip} puntos y caja de Gs ${caja}M.`;

    if (ctx.eventos_activos.length > 0) {
      texto += ` Hay ${ctx.eventos_activos.length} evento(s) activo(s) este trimestre que afectan tus decisiones.`;
    }

    texto += ` [Proximamente este resumen sera generado por IA con analisis personalizado de tu situacion competitiva.]`;
    return texto;
  }

  get cierreEn(): string {
    const diff = new Date(this.trimestre.cierre_at).getTime() - Date.now();
    if (diff <= 0) return 'Vencido';
    const days = Math.floor(diff / 86400000);
    const hours = Math.floor((diff % 86400000) / 3600000);
    return `${days}d ${hours}h`;
  }
}

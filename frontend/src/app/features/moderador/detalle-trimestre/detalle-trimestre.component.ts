import { Component, inject, OnInit, signal, computed, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { forkJoin } from 'rxjs';
import { LucideAngularModule, Zap, Lock, Clock, Check, ChevronLeft } from 'lucide-angular';
import Swal from 'sweetalert2';
import { EstadoChipComponent } from '../../../shared/components/estado-chip/estado-chip.component';
import { CompetenciaApiService } from '../../../core/services/competencia-api.service';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { TrimestreApiService } from '../../../core/services/trimestre-api.service';
import { DecisionApiService } from '../../../core/services/decision-api.service';
import { EventoApiService } from '../../../core/services/evento-api.service';
import { CompetenciaDetalle } from '../../../core/models/competencia.model';
import { Trimestre } from '../../../core/models/trimestre.model';
import { Decision } from '../../../core/models/decision.model';
import { EventoCompetencia, Severidad } from '../../../core/models/evento.model';
import { Equipo } from '../../../core/models/equipo.model';

interface EquipoDecisionRow {
  equipoId: number;
  nombre: string;
  color: string;
  iniciales: string;
  estado: 'ENVIADA' | 'BORRADOR' | 'PENDIENTE';
  estadoLabel: string;
  hora: string;
}

@Component({
  selector: 'app-detalle-trimestre-mod',
  standalone: true,
  imports: [DatePipe, RouterLink, EstadoChipComponent, LucideAngularModule],
  templateUrl: './detalle-trimestre.component.html',
})
export class DetalleTrimestreModComponent implements OnInit, OnDestroy {
  readonly icons = { Zap, Lock, Clock, Check, ChevronLeft };

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private competenciaApi = inject(CompetenciaApiService);
  private competenciaStore = inject(CompetenciaStore);
  private trimestreApi = inject(TrimestreApiService);
  private decisionApi = inject(DecisionApiService);
  private eventoApi = inject(EventoApiService);

  loading = signal(true);
  actionLoading = signal(false);
  trimestre = signal<Trimestre | null>(null);
  competencia = signal<CompetenciaDetalle | null>(null);
  equiposDecisiones = signal<EquipoDecisionRow[]>([]);
  eventosActivos = signal<EventoCompetencia[]>([]);
  countdown = signal('');
  private countdownInterval: any;

  decisionesEnviadas = computed(() =>
    this.equiposDecisiones().filter(e => e.estado === 'ENVIADA').length
  );
  totalEquipos = computed(() => this.equiposDecisiones().length);

  canCerrar = computed(() => this.trimestre()?.estado === 'ABIERTO_DECISIONES');

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.loadData(id);
  }

  ngOnDestroy(): void {
    if (this.countdownInterval) clearInterval(this.countdownInterval);
  }

  private loadData(trimestreId: number): void {
    this.loading.set(true);
    this.trimestreApi.getById(trimestreId).subscribe({
      next: (t) => {
        this.trimestre.set(t);
        const competenciaId = t.competencia_id;

        forkJoin({
          competencia: this.competenciaApi.getById(competenciaId),
          decisiones: this.decisionApi.listByTrimestre(trimestreId),
          eventos: this.eventoApi.listByCompetencia(competenciaId, trimestreId),
        }).subscribe({
          next: ({ competencia, decisiones, eventos }) => {
            this.competencia.set(competencia);
            this.competenciaStore.competenciaActiva.set(competencia);
            this.eventosActivos.set(eventos);
            this.buildEquiposDecisiones(competencia.equipos, decisiones);
            this.startCountdown(t);
            this.loading.set(false);
          },
          error: () => this.loading.set(false),
        });
      },
      error: () => this.loading.set(false),
    });
  }

  private buildEquiposDecisiones(equipos: Equipo[], decisiones: Decision[]): void {
    this.equiposDecisiones.set(equipos.map(e => {
      const dec = decisiones.find(d => d.equipo_id === e.id);
      const iniciales = e.nombre_empresa.split(' ').filter(Boolean).map(w => w[0]).join('').substring(0, 2).toUpperCase();
      let estado: EquipoDecisionRow['estado'] = 'PENDIENTE';
      let estadoLabel = 'Pendiente...';
      let hora = '—';
      if (dec) {
        if (dec.estado === 'ENVIADA' || dec.estado === 'PROCESADA') {
          estado = 'ENVIADA';
          estadoLabel = 'Enviada';
          hora = dec.submitted_at
            ? new Date(dec.submitted_at).toLocaleDateString('es-PY', { day: '2-digit', month: '2-digit', year: 'numeric' }) + ' ' +
              new Date(dec.submitted_at).toLocaleTimeString('es-PY', { hour: '2-digit', minute: '2-digit' })
            : '';
        } else {
          estado = 'BORRADOR';
          estadoLabel = 'Borrador';
        }
      }
      return { equipoId: e.id, nombre: e.nombre_empresa, color: e.codigo_color, iniciales, estado, estadoLabel, hora };
    }));
  }

  private startCountdown(t: Trimestre): void {
    if (this.countdownInterval) clearInterval(this.countdownInterval);
    if (t.estado !== 'ABIERTO_DECISIONES' || !t.cierre_at) {
      this.countdown.set('');
      return;
    }
    const update = () => {
      const now = Date.now();
      const cierre = new Date(t.cierre_at!).getTime();
      const diff = cierre - now;
      if (diff <= 0) {
        this.countdown.set('Vencido');
        clearInterval(this.countdownInterval);
        return;
      }
      const days = Math.floor(diff / (1000 * 60 * 60 * 24));
      const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
      const mins = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
      if (days > 0) {
        this.countdown.set(`Cierra en ${days}d ${hours}h`);
      } else {
        this.countdown.set(`Cierra en ${hours}h ${mins}m`);
      }
    };
    update();
    this.countdownInterval = setInterval(update, 60000);
  }

  severidadClass(severidad: Severidad): string {
    switch (severidad) {
      case 'GRAVE': return 'bg-red-100 text-red-700';
      case 'MODERADO': return 'bg-amber-100 text-amber-700';
      case 'LEVE': return 'bg-blue-100 text-blue-700';
      case 'POSITIVO': return 'bg-green-100 text-green-700';
      default: return 'bg-gray-100 text-gray-600';
    }
  }

  severidadLabel(severidad: Severidad): string {
    switch (severidad) {
      case 'GRAVE': return 'Severo';
      case 'MODERADO': return 'Moderado';
      case 'LEVE': return 'Leve';
      case 'POSITIVO': return 'Positivo';
      default: return severidad;
    }
  }

  cerrarYProcesar(): void {
    const t = this.trimestre();
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
        this.trimestreApi.cerrar(t.id).subscribe({
          next: () => {
            this.actionLoading.set(false);
            Swal.fire({ icon: 'success', title: `Q${t.numero} procesado`, timer: 2000, showConfirmButton: false });
            this.loadData(t.id);
          },
          error: (err) => {
            this.actionLoading.set(false);
            Swal.fire({ icon: 'error', title: 'Error', text: err.error?.detail ?? 'Error al cerrar trimestre', confirmButtonColor: '#006B3F' });
          },
        });
      }
    });
  }

  volver(): void {
    this.router.navigate(['/moderador/trimestres'], {
      queryParams: { competencia: this.competencia()?.id },
    });
  }
}

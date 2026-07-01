import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { LucideAngularModule, Clock, Trophy, ChevronRight } from 'lucide-angular';
import { EstadoChipComponent } from '../../../shared/components/estado-chip/estado-chip.component';
import { JugadorStore } from '../../../core/stores/jugador.store';
import { Competencia } from '../../../core/models/competencia.model';

@Component({
  selector: 'app-seleccionar-competencia',
  standalone: true,
  imports: [LucideAngularModule, EstadoChipComponent],
  templateUrl: './seleccionar-competencia.component.html',
})
export class SeleccionarCompetenciaComponent implements OnInit {
  readonly icons = { Clock, Trophy, ChevronRight };

  private jugadorStore = inject(JugadorStore);
  private router = inject(Router);

  loading = signal(true);
  competencias = signal<Competencia[]>([]);

  async ngOnInit(): Promise<void> {
    await this.jugadorStore.init();
    const todas = this.jugadorStore.misCompetencias();

    // Si solo tiene una competencia, ir directo al dashboard
    if (todas.length <= 1) {
      this.router.navigate(['/jugador/competencia']);
      return;
    }

    const enCurso = todas.filter(c => c.estado === 'EN_CURSO' || c.estado === 'PENDIENTE_FINALIZAR');
    const finalizadas = todas
      .filter(c => c.estado === 'FINALIZADA')
      .sort((a, b) => (b.cierre_at ?? '').localeCompare(a.cierre_at ?? ''));

    let relevantes: Competencia[];

    if (enCurso.length > 0) {
      // Tiene competencias en curso: mostrar todas en curso + la ultima finalizada
      const ultimaFinalizada = finalizadas[0];
      relevantes = ultimaFinalizada ? [...enCurso, ultimaFinalizada] : enCurso;
    } else {
      // Sin competencias en curso: mostrar las 3 finalizadas mas recientes
      relevantes = finalizadas.slice(0, 3);
    }

    this.competencias.set(relevantes);
    this.loading.set(false);

    // Si quedo solo 1 relevante, ir directo al dashboard
    if (relevantes.length <= 1) {
      if (relevantes.length === 1) {
        await this.jugadorStore.switchCompetencia(relevantes[0].id);
      }
      this.router.navigate(['/jugador/competencia']);
      return;
    }
  }

  async seleccionar(competenciaId: number): Promise<void> {
    await this.jugadorStore.switchCompetencia(competenciaId);
    this.router.navigate(['/jugador/competencia']);
  }

  isEnCurso(c: Competencia): boolean {
    return c.estado === 'EN_CURSO';
  }

  competenciaActivaId = computed(() => this.jugadorStore.competencia()?.id ?? null);
}

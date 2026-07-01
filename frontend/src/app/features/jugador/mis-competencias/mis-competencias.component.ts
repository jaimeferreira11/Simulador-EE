import { Component, inject, computed, signal, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { LucideAngularModule, Trophy, Clock, Users, ChevronRight } from 'lucide-angular';
import { EstadoChipComponent } from '../../../shared/components/estado-chip/estado-chip.component';
import { JugadorStore } from '../../../core/stores/jugador.store';
import { Competencia } from '../../../core/models/competencia.model';

@Component({
  selector: 'app-mis-competencias',
  standalone: true,
  imports: [EstadoChipComponent, LucideAngularModule],
  templateUrl: './mis-competencias.component.html',
})
export class MisCompetenciasComponent implements OnInit {
  readonly icons = { Trophy, Clock, Users, ChevronRight };

  private jugadorStore = inject(JugadorStore);
  private router = inject(Router);

  competencias = signal<Competencia[]>([]);
  competenciaActivaId = computed(() => this.jugadorStore.competencia()?.id ?? null);
  loading = this.jugadorStore.loading;

  ngOnInit(): void {
    const todas = this.jugadorStore.misCompetencias();
    const enCurso = todas.filter(c => c.estado === 'EN_CURSO' || c.estado === 'PENDIENTE_FINALIZAR');
    const finalizadas = todas
      .filter(c => c.estado === 'FINALIZADA')
      .sort((a, b) => (b.cierre_at ?? '').localeCompare(a.cierre_at ?? ''));

    if (enCurso.length > 0) {
      const ultimaFinalizada = finalizadas[0];
      this.competencias.set(ultimaFinalizada ? [...enCurso, ultimaFinalizada] : enCurso);
    } else {
      this.competencias.set(finalizadas.slice(0, 3));
    }
  }

  async seleccionar(competenciaId: number): Promise<void> {
    await this.jugadorStore.switchCompetencia(competenciaId);
    this.router.navigate(['/jugador/competencia']);
  }

  isActiva(competenciaId: number): boolean {
    return this.competenciaActivaId() === competenciaId;
  }
}

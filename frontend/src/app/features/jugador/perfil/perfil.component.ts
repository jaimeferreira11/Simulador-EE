import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { LucideAngularModule, Building2, Pencil, Lock, PlayCircle } from 'lucide-angular';
import { AuthStore } from '../../../core/stores/auth.store';
import { CompetenciaApiService } from '../../../core/services/competencia-api.service';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { Competencia } from '../../../core/models/competencia.model';
import { OnboardingService } from '../../../shared/services/onboarding.service';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [LucideAngularModule, DatePipe],
  templateUrl: './perfil.component.html',
})
export class PerfilComponent implements OnInit {
  readonly icons = { Building2, Pencil, Lock, PlayCircle };

  private authStore = inject(AuthStore);
  private competenciaApi = inject(CompetenciaApiService);
  private competenciaStore = inject(CompetenciaStore);
  private onboarding = inject(OnboardingService);

  user = this.authStore.user;
  competencias = signal<Competencia[]>([]);
  loading = signal(true);

  iniciales = computed(() => {
    const nombre = this.user()?.nombre_completo ?? '';
    return nombre
      .split(' ')
      .filter(Boolean)
      .map(p => p[0])
      .join('')
      .substring(0, 2)
      .toUpperCase();
  });

  nombreEquipo = computed(() => {
    const comp = this.competenciaStore.competenciaActiva();
    if (!comp) return null;
    const userId = this.user()?.id;
    const equipo = comp.equipos.find(e =>
      (e as any).miembros?.some((m: any) => m.usuario_id === userId)
    );
    return equipo?.nombre_empresa ?? null;
  });

  totalCompetencias = computed(() => this.competencias().length);

  trimestresCompletados = computed(() => {
    return this.competencias().reduce((sum, c) => sum + (c.num_trimestres ?? 0), 0);
  });

  miembroDesde = computed(() => this.user()?.created_at ?? null);

  ngOnInit(): void {
    this.loadCompetencias();
  }

  private loadCompetencias(): void {
    this.competenciaApi.list(0, 100).subscribe({
      next: (page) => {
        this.competencias.set(page.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  verTutorialOtraVez(): void {
    this.onboarding.start();
  }
}

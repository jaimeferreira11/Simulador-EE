import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { forkJoin, Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { LucideAngularModule, AlertTriangle, UserPlus, X, Shield, Trophy, Send } from 'lucide-angular';
import Swal from 'sweetalert2';
import { EstadoChipComponent } from '../../../shared/components/estado-chip/estado-chip.component';
import { BotBadgeComponent } from '../../../shared/components/bot-badge/bot-badge.component';
import { EquipoApiService } from '../../../core/services/equipo-api.service';
import { CompetenciaApiService } from '../../../core/services/competencia-api.service';
import { ResultadoApiService } from '../../../core/services/resultado-api.service';
import { UsuarioApiService } from '../../../core/services/usuario-api.service';
import { InvitacionApiService } from '../../../core/services/invitacion-api.service';
import { CatalogoApiService } from '../../../core/services/catalogo-api.service';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { EquipoDetalle, EquipoMiembro } from '../../../core/models/equipo.model';
import { CompetenciaDetalle } from '../../../core/models/competencia.model';
import { RankingItem } from '../../../core/models/resultado.model';
import { Usuario } from '../../../core/models/usuario.model';
import { AreaDecision } from '../../../core/models/catalogo.model';
import { GuaraniCortoPipe } from '../../../core/pipes/guarani-corto.pipe';

@Component({
  selector: 'app-detalle-equipo-mod',
  standalone: true,
  imports: [FormsModule, EstadoChipComponent, BotBadgeComponent, LucideAngularModule, GuaraniCortoPipe],
  templateUrl: './detalle-equipo.component.html',
})
export class DetalleEquipoModComponent implements OnInit {
  readonly icons = { AlertTriangle, UserPlus, X, Shield, Trophy, Send };

  private route = inject(ActivatedRoute);
  private equipoApi = inject(EquipoApiService);
  private competenciaApi = inject(CompetenciaApiService);
  private resultadoApi = inject(ResultadoApiService);
  private usuarioApi = inject(UsuarioApiService);
  private invitacionApi = inject(InvitacionApiService);
  private catalogoApi = inject(CatalogoApiService);
  private competenciaStore = inject(CompetenciaStore);

  loading = signal(true);
  equipo = signal<EquipoDetalle | null>(null);
  competencia = signal<CompetenciaDetalle | null>(null);
  rankingItem = signal<RankingItem | null>(null);
  totalEquipos = signal(0);
  trimestreActualNumero = signal(0);
  actionLoading = signal(false);
  error = signal<string | null>(null);
  areas = signal<AreaDecision[]>([]);

  // Add member dialog
  showAgregar = signal(false);
  agregarEmail = '';
  agregarNombre = '';
  agregarAreaId: number | null = null;
  agregarEsCapitan = false;
  agregarSaving = signal(false);

  // Email search
  emailSearch$ = new Subject<string>();
  buscandoEmail = signal(false);
  usuarioEncontrado = signal<Usuario | null>(null);
  usuarioNoExiste = signal(false);

  // Intervene dialog
  showIntervenir = signal(false);
  intervenirJustificacion = '';

  iniciales = computed(() => {
    const e = this.equipo();
    if (!e) return '';
    return e.nombre_empresa.split(' ').filter(Boolean).map(w => w[0]).join('').substring(0, 2).toUpperCase();
  });

  miembros = computed(() => this.equipo()?.miembros ?? []);
  maxMiembros = signal(5);

  puedeAgregar = computed(() => {
    const estado = this.competencia()?.estado;
    return estado === 'BORRADOR' || estado === 'ABIERTA_INSCRIPCION';
  });

  miembroIniciales(m: EquipoMiembro): string {
    return m.usuario.nombre_completo.split(' ').slice(0, 2).map(w => w[0]).join('').toUpperCase();
  }

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.loadEquipo(id);
    this.loadAreas();
    this.setupEmailSearch();
  }

  private loadAreas(): void {
    this.catalogoApi.getAreas().subscribe({
      next: (areas) => this.areas.set(areas),
    });
  }

  private setupEmailSearch(): void {
    this.emailSearch$
      .pipe(
        debounceTime(400),
        distinctUntilChanged(),
        switchMap((email) => {
          if (!email || !email.includes('@')) {
            this.usuarioEncontrado.set(null);
            this.usuarioNoExiste.set(false);
            this.buscandoEmail.set(false);
            return [];
          }
          this.buscandoEmail.set(true);
          return this.usuarioApi.search(email);
        }),
      )
      .subscribe({
        next: (result) => {
          this.buscandoEmail.set(false);
          const exactMatch = result.content.find(
            (u) => u.email.toLowerCase() === this.agregarEmail.trim().toLowerCase(),
          );
          if (exactMatch) {
            this.usuarioEncontrado.set(exactMatch);
            this.agregarNombre = exactMatch.nombre_completo;
          } else {
            this.usuarioEncontrado.set(null);
            this.usuarioNoExiste.set(true);
          }
        },
      });
  }

  onEmailInput(email: string): void {
    this.agregarEmail = email;
    this.usuarioEncontrado.set(null);
    this.usuarioNoExiste.set(false);
    this.agregarNombre = '';
    this.emailSearch$.next(email.trim().toLowerCase());
  }

  private loadEquipo(id: number): void {
    this.loading.set(true);
    this.equipoApi.getById(id).subscribe({
      next: (e) => {
        this.equipo.set(e);
        this.loadCompetenciaData(e.competencia_id, e.id);
      },
      error: () => this.loading.set(false),
    });
  }

  private loadCompetenciaData(competenciaId: number, equipoId: number): void {
    forkJoin({
      competencia: this.competenciaApi.getById(competenciaId),
      ranking: this.resultadoApi.getRanking(competenciaId),
    }).subscribe({
      next: ({ competencia, ranking }) => {
        this.competencia.set(competencia);
        this.competenciaStore.competenciaActiva.set(competencia);
        this.totalEquipos.set(competencia.equipos?.length ?? 0);

        const current = competencia.trimestres.find(t =>
          t.estado === 'ABIERTO_DECISIONES' || t.estado === 'CERRADO_PROCESANDO'
        );
        const lastProcessed = competencia.trimestres
          .filter(t => t.estado === 'PROCESADO')
          .pop();
        this.trimestreActualNumero.set(current?.numero ?? lastProcessed?.numero ?? 0);

        const item = ranking.find(r => r.equipo_id === equipoId) ?? null;
        this.rankingItem.set(item);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  // --- Agregar miembro ---
  abrirAgregar(): void {
    this.agregarEmail = '';
    this.agregarNombre = '';
    this.agregarAreaId = null;
    this.agregarEsCapitan = false;
    this.usuarioEncontrado.set(null);
    this.usuarioNoExiste.set(false);
    this.buscandoEmail.set(false);
    this.showAgregar.set(true);
  }

  formAgregarValido(): boolean {
    return !!this.agregarEmail.trim() && !!this.agregarNombre.trim();
  }

  agregarMiembro(): void {
    if (!this.formAgregarValido()) return;
    const equipoId = this.equipo()!.id;
    this.agregarSaving.set(true);

    const usuario = this.usuarioEncontrado();

    if (usuario) {
      this.equipoApi
        .addMiembro(equipoId, {
          usuario_id: usuario.id,
          es_capitan: this.agregarEsCapitan,
          area_id: this.agregarAreaId,
        })
        .subscribe({
          next: () => {
            this.agregarSaving.set(false);
            this.showAgregar.set(false);
            this.loadEquipo(equipoId);
          },
          error: (err) => {
            this.agregarSaving.set(false);
            Swal.fire({
              title: 'Error al agregar',
              text: err.error?.detail ?? 'Ocurrió un error inesperado',
              icon: 'error',
              confirmButtonColor: '#006B3F',
            });
          },
        });
    } else {
      this.invitacionApi
        .invitar(equipoId, {
          email: this.agregarEmail.trim().toLowerCase(),
          nombreCompleto: this.agregarNombre.trim(),
          areaId: this.agregarAreaId,
          esCapitan: this.agregarEsCapitan,
        })
        .subscribe({
          next: () => {
            this.agregarSaving.set(false);
            this.showAgregar.set(false);
            this.loadEquipo(equipoId);
          },
          error: (err) => {
            this.agregarSaving.set(false);
            Swal.fire({
              title: 'Error al invitar',
              text: err.error?.detail ?? 'Ocurrió un error inesperado',
              icon: 'error',
              confirmButtonColor: '#006B3F',
            });
          },
        });
    }
  }

  removerMiembro(m: EquipoMiembro): void {
    Swal.fire({
      title: 'Remover miembro',
      text: `¿Remover a ${m.usuario.nombre_completo} del equipo?`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Remover',
      cancelButtonText: 'Cancelar',
      confirmButtonColor: '#DC2626',
    }).then((result) => {
      if (result.isConfirmed) {
        this.actionLoading.set(true);
        this.equipoApi.removeMiembro(this.equipo()!.id, m.id).subscribe({
          next: () => {
            this.actionLoading.set(false);
            this.loadEquipo(this.equipo()!.id);
          },
          error: (err) => {
            this.actionLoading.set(false);
            this.error.set(err.error?.detail ?? 'Error al remover miembro');
          },
        });
      }
    });
  }

  hacerCapitan(m: EquipoMiembro): void {
    this.actionLoading.set(true);
    this.equipoApi.setCapitan(this.equipo()!.id, m.id).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.loadEquipo(this.equipo()!.id);
      },
      error: (err) => {
        this.actionLoading.set(false);
        this.error.set(err.error?.detail ?? 'Error al asignar capitán');
      },
    });
  }

  intervenir(): void {
    if (!this.intervenirJustificacion.trim()) return;
    if (this.intervenirJustificacion.trim().length < 20) {
      this.error.set('La justificación debe tener al menos 20 caracteres');
      return;
    }
    this.actionLoading.set(true);
    this.error.set(null);
    this.equipoApi.updateEstado(this.equipo()!.id, 'INTERVENIDO', this.intervenirJustificacion.trim()).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.showIntervenir.set(false);
        this.intervenirJustificacion = '';
        Swal.fire({ icon: 'success', title: 'Equipo intervenido', timer: 2000, showConfirmButton: false });
        this.loadEquipo(this.equipo()!.id);
      },
      error: (err) => {
        this.actionLoading.set(false);
        this.error.set(err.error?.detail ?? 'Error al intervenir equipo');
      },
    });
  }
}

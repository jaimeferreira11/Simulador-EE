import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin, catchError, of, firstValueFrom } from 'rxjs';
import Swal from 'sweetalert2';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { JugadorStore } from '../../../core/stores/jugador.store';
import { CompetenciaStore } from '../../../core/stores/competencia.store';
import { DecisionApiService } from '../../../core/services/decision-api.service';
import { DemoService } from '../../../core/services/demo.service';
import { Decision, DecisionInput } from '../../../core/models/decision.model';
import {
  ContextoDecision,
  ProyeccionFinanciera,
} from '../../../core/models/contexto-decision.model';
import { BriefingComponent } from './briefing/briefing.component';
import { DepartamentosComponent } from './departamentos/departamentos.component';
import { RevisionComponent } from './revision/revision.component';
import {
  AdvanceTrimesterDialogComponent,
  AdvanceTrimesterDialogData,
} from '../../../shared/components/advance-trimester-dialog/advance-trimester-dialog.component';
import { AdvanceTrimesterFabComponent } from '../../../shared/components/advance-trimester-fab/advance-trimester-fab.component';

type Vista = 'briefing' | 'departamentos' | 'revision';

@Component({
  selector: 'app-decisiones',
  standalone: true,
  imports: [
    BriefingComponent,
    DepartamentosComponent,
    RevisionComponent,
    MatDialogModule,
    AdvanceTrimesterFabComponent,
  ],
  templateUrl: './decisiones.component.html',
})
export class DecisionesComponent implements OnInit {
  private jugadorStore = inject(JugadorStore);
  private competenciaStore = inject(CompetenciaStore);
  private decisionApi = inject(DecisionApiService);
  private demo = inject(DemoService);
  private dialog = inject(MatDialog);
  private router = inject(Router);

  /** Visible solo en demo: mostrar FAB tras "No, después". */
  demoFabVisible = signal(false);
  /** Visible solo en demo: avance en curso. */
  demoAvanzando = signal(false);

  isDemo = computed(() => this.demo.isDemo(this.competenciaStore.competenciaActiva()));

  loading = signal(true);
  error = signal<string | null>(null);
  vista = signal<Vista>('briefing');
  contexto = signal<ContextoDecision | null>(null);
  decision = signal<Decision | null>(null);
  formValues = signal<Record<string, number | null>>({});
  proyeccion = signal<ProyeccionFinanciera | null>(null);
  saving = signal(false);

  equipo = this.jugadorStore.equipo;
  trimestreActual = this.jugadorStore.trimestreActual;

  esEnviada = computed(() => this.decision()?.estado === 'ENVIADA');
  esProcesada = computed(() => this.decision()?.estado === 'PROCESADA');
  soloLectura = computed(() => {
    const trim = this.trimestreActual();
    if (!trim) return true;
    return trim.estado !== 'ABIERTO_DECISIONES' || this.esEnviada() || this.esProcesada();
  });

  ngOnInit(): void {
    this.initData();
  }

  private async initData(): Promise<void> {
    try {
      await this.jugadorStore.init();
    } catch {
      this.error.set('No se pudo conectar con el servidor');
      this.loading.set(false);
      return;
    }

    const eq = this.equipo();
    const trim = this.trimestreActual();
    if (!eq || !trim) {
      this.error.set('No tienes un equipo o trimestre activo asignado');
      this.loading.set(false);
      return;
    }

    forkJoin({
      ctx: this.decisionApi.getContextoDecision(eq.id, trim.id),
      decision: this.decisionApi.get(eq.id, trim.id).pipe(catchError(() => of(null))),
    }).subscribe({
      next: ({ ctx, decision }) => {
        this.contexto.set(ctx);

        if (decision) {
          this.decision.set(decision);
          this.loadFormValues(decision);
        } else if (ctx.decision_anterior) {
          const vals: Record<string, number | null> = {};
          for (const [key, val] of Object.entries(ctx.decision_anterior)) {
            vals[key] = val ?? null;
          }
          this.formValues.set(vals);
        }

        this.actualizarProyeccion();
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Error al cargar el contexto de decisiones');
        this.loading.set(false);
      },
    });
  }

  private loadFormValues(d: Decision): void {
    const fields: (keyof DecisionInput)[] = [
      'precio_venta',
      'produccion_planificada',
      'inversion_marketing',
      'contrataciones_netas',
      'aumento_salarial_pct',
      'inversion_capacitacion',
      'prestamo_solicitado',
      'inversion_capacidad',
      'inversion_id',
    ];
    const vals: Record<string, number | null> = {};
    for (const f of fields) {
      vals[f] = (d as any)[f] ?? null;
    }
    this.formValues.set(vals);
  }

  irADecidir(): void {
    this.vista.set('departamentos');
  }

  irARevisar(): void {
    this.vista.set('revision');
  }

  irAEditar(): void {
    this.vista.set('departamentos');
  }

  onFieldChange(field: string, value: number | null): void {
    this.formValues.update((v) => ({ ...v, [field]: value }));
    this.actualizarProyeccion();
  }

  private proyeccionTimeout: any;
  private actualizarProyeccion(): void {
    clearTimeout(this.proyeccionTimeout);
    this.proyeccionTimeout = setTimeout(() => {
      const input = this.buildInput();
      const eq = this.equipo();
      const trim = this.trimestreActual();
      if (!eq || !trim) return;
      this.decisionApi.getProyeccion(eq.id, trim.id, input).subscribe({
        next: (p) => this.proyeccion.set(p),
      });
    }, 500);
  }

  guardar(): void {
    const eq = this.equipo();
    const trim = this.trimestreActual();
    if (!eq || !trim) return;

    this.saving.set(true);
    const input = this.buildInput();
    this.decisionApi.upsert(eq.id, trim.id, input).subscribe({
      next: (d) => {
        this.decision.set(d);
        this.saving.set(false);
        Swal.fire({
          title: 'Borrador guardado',
          text: 'Tus decisiones fueron guardadas correctamente.',
          icon: 'success',
          confirmButtonColor: '#006B3F',
          timer: 2000,
          showConfirmButton: false,
        });
      },
      error: () => {
        this.saving.set(false);
        Swal.fire({
          title: 'Error',
          text: 'No se pudo guardar el borrador. Intentá de nuevo.',
          icon: 'error',
          confirmButtonColor: '#006B3F',
        });
      },
    });
  }

  enviar(): void {
    if (this.isDemo()) {
      this.enviarComoDemoCeo();
      return;
    }

    const eq = this.equipo();
    const trim = this.trimestreActual();
    if (!eq || !trim) return;

    this.saving.set(true);
    const input = this.buildInput();
    this.decisionApi.upsert(eq.id, trim.id, input).subscribe({
      next: () => {
        this.decisionApi.enviar(eq.id, trim.id).subscribe({
          next: (d) => {
            this.decision.set(d);
            this.saving.set(false);
            Swal.fire({
              title: '¡Decisiones enviadas!',
              text: 'Tus decisiones fueron enviadas exitosamente. Buena suerte este trimestre.',
              icon: 'success',
              confirmButtonText: 'Ir al Dashboard',
              confirmButtonColor: '#006B3F',
            }).then(() => {
              this.router.navigate(['/jugador/competencia']);
            });
          },
          error: (err) => {
            this.saving.set(false);
            const msg = err?.error?.detail ?? 'Error al enviar las decisiones';
            Swal.fire({
              title: 'Error al enviar',
              text: msg,
              icon: 'error',
              confirmButtonColor: '#006B3F',
            });
          },
        });
      },
      error: () => {
        this.saving.set(false);
        Swal.fire({
          title: 'Error',
          text: 'Error al guardar antes de enviar. Intentá de nuevo.',
          icon: 'error',
          confirmButtonColor: '#006B3F',
        });
      },
    });
  }

  /**
   * Flujo de envío específico de la competencia DEMO:
   *   1. POST /demo/decision-ceo con el payload del form.
   *   2. Abrir diálogo "¿Avanzar al trimestre Q{N+1}?".
   *   3. "Sí" → POST /demo/avanzar. "No" → mostrar FAB.
   *
   * Toda la auth y la asignación al equipo HUMANO la resuelve el backend
   * (no necesitamos `eq` ni `decisionApi.upsert` aquí).
   */
  private async enviarComoDemoCeo(): Promise<void> {
    const comp = this.competenciaStore.competenciaActiva();
    const trim = this.trimestreActual();
    if (!comp || !trim) return;

    this.saving.set(true);
    const payload = this.buildInput() as unknown as Record<string, unknown>;
    try {
      const d = await firstValueFrom(this.demo.decisionCeo(comp.id, payload));
      this.decision.set(d);
    } catch (err: any) {
      this.saving.set(false);
      Swal.fire({
        title: 'Error al enviar',
        text: err?.error?.detail ?? 'No se pudo enviar la decisión del CEO',
        icon: 'error',
        confirmButtonColor: '#006B3F',
      });
      return;
    }
    this.saving.set(false);

    const data: AdvanceTrimesterDialogData = { proximoTrimestre: trim.numero + 1 };
    const ref = this.dialog.open(AdvanceTrimesterDialogComponent, { data, width: '480px' });
    const res = await firstValueFrom(ref.afterClosed());
    if (res?.confirmed) {
      await this.avanzarDemo();
    } else {
      this.demoFabVisible.set(true);
    }
  }

  /** Llamado por el FAB. */
  onAvanzarDemoClick(): void {
    void this.avanzarDemo();
  }

  private async avanzarDemo(): Promise<void> {
    const comp = this.competenciaStore.competenciaActiva();
    if (!comp) return;
    this.demoAvanzando.set(true);
    try {
      await firstValueFrom(this.demo.avanzar(comp.id));
      await this.competenciaStore.reload();
      this.demoFabVisible.set(false);
      // Recargar contexto del nuevo trimestre y volver al briefing.
      this.vista.set('briefing');
      this.decision.set(null);
      this.formValues.set({});
      this.loading.set(true);
      await this.initData();
    } catch (err: any) {
      Swal.fire({
        title: 'Error al avanzar',
        text: err?.error?.detail ?? 'No se pudo avanzar el trimestre',
        icon: 'error',
        confirmButtonColor: '#006B3F',
      });
    } finally {
      this.demoAvanzando.set(false);
    }
  }

  private buildInput(): DecisionInput {
    const v = this.formValues();
    return {
      precio_venta: v['precio_venta'] ?? 0,
      produccion_planificada: v['produccion_planificada'] ?? undefined,
      inversion_marketing: v['inversion_marketing'] ?? undefined,
      contrataciones_netas: v['contrataciones_netas'] ?? undefined,
      aumento_salarial_pct: v['aumento_salarial_pct'] ?? undefined,
      inversion_capacitacion: v['inversion_capacitacion'] ?? undefined,
      prestamo_solicitado: v['prestamo_solicitado'] ?? undefined,
      dividendos_pagar: 0, // Omitido del flujo del jugador: siempre 0 hacia el motor.
      inversion_capacidad: v['inversion_capacidad'] ?? undefined,
      inversion_id: v['inversion_id'] ?? undefined,
    };
  }
}

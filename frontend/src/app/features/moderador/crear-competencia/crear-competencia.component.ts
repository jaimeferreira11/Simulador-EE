import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Check, Info, Layout, Zap, TrendingDown, Target, Settings, X, Bot } from 'lucide-angular';
import { CatalogoApiService } from '../../../core/services/catalogo-api.service';
import { CompetenciaApiService } from '../../../core/services/competencia-api.service';
import { Rubro, ParametroMacro, ParametroRubro } from '../../../core/models/catalogo.model';
import { Entidad, EscenarioPredefinido } from '../../../core/models/competencia.model';
import { DecimalPipe } from '@angular/common';
import { GuaraniPipe } from '../../../shared/pipes/guarani.pipe';
import Swal from 'sweetalert2';

@Component({
  selector: 'app-crear-competencia',
  standalone: true,
  imports: [FormsModule, LucideAngularModule, GuaraniPipe, DecimalPipe],
  templateUrl: './crear-competencia.component.html',
})
export class CrearCompetenciaComponent implements OnInit {
  readonly icons = { Check, Info, Layout, Zap, TrendingDown, Target, Settings, X, Bot };

  private catalogoApi = inject(CatalogoApiService);
  private competenciaApi = inject(CompetenciaApiService);
  private router = inject(Router);

  pasoActual = 1;
  pasos = [
    { num: 1, label: 'Datos Básicos' },
    { num: 2, label: 'Configuración' },
    { num: 3, label: 'Revisión' },
  ];

  rubros = signal<Rubro[]>([]);
  parametrosMacro = signal<ParametroMacro[]>([]);
  entidades = signal<Entidad[]>([]);
  escenarios = signal<EscenarioPredefinido[]>([]);
  escenarioSeleccionado = signal<EscenarioPredefinido | null>(null);
  saving = signal(false);
  error = signal<string | null>(null);

  form: {
    nombre: string;
    entidad_id: number;
    rubro_id: number;
    parametro_macro_id: number;
    parametro_rubro_id: number;
    num_trimestres: number;
    max_integrantes_equipo: number | null;
    caja_inicial: number;
    capacidad_inicial: number;
    headcount_inicial: number;
    salario_inicial: number;
    valor_planta_inicial: number;
    escenario_id?: number;
    ia_habilitada: boolean;
  } = {
    nombre: '',
    entidad_id: 0,
    rubro_id: 0,
    parametro_macro_id: 0,
    parametro_rubro_id: 0,
    num_trimestres: 4,
    max_integrantes_equipo: null,
    caja_inicial: 500000000,
    capacidad_inicial: 50000,
    headcount_inicial: 100,
    salario_inicial: 3500000,
    valor_planta_inicial: 2500000000,
    ia_habilitada: false,
  };

  rubroNombre = '';
  entidadNombre = '';

  /** Display strings for currency inputs (formatted with thousands separators) */
  displayCaja = '';
  displaySalario = '';
  displayPlanta = '';

  ngOnInit(): void {
    this.syncDisplayValues();

    this.competenciaApi.listEntidades().subscribe({
      next: (entidades) => {
        this.entidades.set(entidades);
        if (entidades.length) {
          this.form.entidad_id = entidades[0].id;
          this.entidadNombre = entidades[0].nombre;
        }
      },
    });

    this.catalogoApi.getRubros().subscribe({
      next: (rubros) => {
        this.rubros.set(rubros);
        if (rubros.length) {
          this.form.rubro_id = rubros[0].id;
          this.rubroNombre = rubros[0].nombre;
          this.autoAssignParametroRubro(rubros[0].id);
          this.loadEscenariosForRubro(rubros[0].id);
        }
      },
    });

    this.catalogoApi.getParametrosMacro().subscribe({
      next: (params) => {
        this.parametrosMacro.set(params);
        if (params.length) this.form.parametro_macro_id = params[0].id;
      },
    });
  }

  seleccionarEscenario(escenario: EscenarioPredefinido): void {
    this.escenarioSeleccionado.set(escenario);
    this.form.escenario_id = escenario.id;
    this.form.num_trimestres = escenario.num_trimestres;
    this.form.caja_inicial = escenario.caja_inicial;
    this.form.capacidad_inicial = escenario.capacidad_inicial;
    this.form.headcount_inicial = escenario.headcount_inicial;
    this.form.salario_inicial = escenario.salario_inicial;
    this.form.valor_planta_inicial = escenario.valor_planta_inicial;
    this.syncDisplayValues();
  }

  quitarEscenario(): void {
    this.escenarioSeleccionado.set(null);
    this.form.escenario_id = undefined;
  }

  dificultadColor(dificultad: string): string {
    switch (dificultad) {
      case 'FACIL': return 'bg-green-100 text-green-700';
      case 'NORMAL': return 'bg-blue-100 text-blue-700';
      case 'DIFICIL': return 'bg-red-100 text-red-700';
      default: return 'bg-gray-100 text-gray-700';
    }
  }

  dificultadLabel(dificultad: string): string {
    switch (dificultad) {
      case 'FACIL': return 'Facil';
      case 'NORMAL': return 'Normal';
      case 'DIFICIL': return 'Dificil';
      default: return dificultad;
    }
  }

  onEntidadChange(): void {
    const entidad = this.entidades().find(e => e.id === this.form.entidad_id);
    this.entidadNombre = entidad?.nombre ?? '';
  }

  onRubroChange(): void {
    const rubro = this.rubros().find((r) => r.id === this.form.rubro_id);
    this.rubroNombre = rubro?.nombre ?? '';
    this.autoAssignParametroRubro(this.form.rubro_id);
    this.quitarEscenario();
    this.loadEscenariosForRubro(this.form.rubro_id);
  }

  /** Auto-assign the first active ParametroRubro for the selected rubro */
  private autoAssignParametroRubro(rubroId: number): void {
    this.catalogoApi.getParametrosRubro(rubroId).subscribe({
      next: (params) => {
        if (params.length) {
          this.form.parametro_rubro_id = params[0].id;
        }
      },
    });
  }

  /** Load escenarios filtered by rubro */
  private loadEscenariosForRubro(rubroId: number): void {
    this.competenciaApi.getEscenarios(rubroId).subscribe({
      next: (escenarios) => this.escenarios.set(escenarios),
      error: () => this.escenarios.set([]),
    });
  }

  /** Format a number as Gs with thousands separators */
  formatGs(value: number): string {
    return value.toLocaleString('es-PY');
  }

  /** Parse a formatted string back to a number */
  parseGs(display: string): number {
    const cleaned = display.replace(/\D/g, '');
    return cleaned ? parseInt(cleaned, 10) : 0;
  }

  onCajaInput(event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    this.form.caja_inicial = this.parseGs(raw);
    this.displayCaja = this.formatGs(this.form.caja_inicial);
  }

  onSalarioInput(event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    this.form.salario_inicial = this.parseGs(raw);
    this.displaySalario = this.formatGs(this.form.salario_inicial);
  }

  onPlantaInput(event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    this.form.valor_planta_inicial = this.parseGs(raw);
    this.displayPlanta = this.formatGs(this.form.valor_planta_inicial);
  }

  private syncDisplayValues(): void {
    this.displayCaja = this.formatGs(this.form.caja_inicial);
    this.displaySalario = this.formatGs(this.form.salario_inicial);
    this.displayPlanta = this.formatGs(this.form.valor_planta_inicial);
  }

  crear(): void {
    this.saving.set(true);
    this.error.set(null);

    // Empty/blank max integrantes => null (sin límite de integrantes por equipo)
    const maxIntegrantes = this.form.max_integrantes_equipo;
    const payload = {
      ...this.form,
      max_integrantes_equipo:
        maxIntegrantes != null && Number(maxIntegrantes) >= 1 ? Number(maxIntegrantes) : null,
    };

    this.competenciaApi.create(payload).subscribe({
      next: (comp) => {
        this.saving.set(false);
        Swal.fire({
          title: 'Competencia creada',
          html:
            `<b>${comp.nombre}</b> fue creada exitosamente con codigo <b>${comp.codigo}</b>.<br><br>` +
            `El siguiente paso es crear los equipos y asignar jugadores.<br>` +
            `<span style="color:#7A7A7A;font-size:13px;">Una vez iniciada la competencia no podras agregar ni modificar equipos.</span>`,
          icon: 'success',
          confirmButtonText: 'Crear Equipos',
          confirmButtonColor: '#006B3F',
        }).then(() => {
          this.router.navigate(['/moderador/equipos'], {
            queryParams: { competencia: comp.id },
          });
        });
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(err.error?.detail ?? 'Error al crear la competencia');
      },
    });
  }
}

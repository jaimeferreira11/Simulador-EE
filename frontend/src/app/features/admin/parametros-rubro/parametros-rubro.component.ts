import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Plus, Search, ChevronLeft, ChevronRight, Pencil, ToggleLeft, ToggleRight, X, Calendar } from 'lucide-angular';
import { AdminApiService } from '../../../core/services/admin-api.service';
import { ParamRubroRow, ParamRubroRequest, RubroTrimestreDto, RubroRow } from '../../../core/models/admin.model';

@Component({
  selector: 'app-admin-parametros-rubro',
  standalone: true,
  imports: [DecimalPipe, FormsModule, LucideAngularModule],
  templateUrl: './parametros-rubro.component.html',
})
export class ParametrosRubroAdminComponent implements OnInit {
  private api = inject(AdminApiService);
  readonly icons = { Plus, Search, ChevronLeft, ChevronRight, Pencil, ToggleLeft, ToggleRight, X, Calendar };

  readonly pageSize = 10;
  rows = signal<ParamRubroRow[]>([]);
  rubros = signal<RubroRow[]>([]);
  loading = signal(true);
  currentPage = signal(0);
  totalElements = signal(0);
  totalPages = signal(0);
  rangeStart = computed(() => this.totalElements() === 0 ? 0 : this.currentPage() * this.pageSize + 1);
  rangeEnd = computed(() => Math.min((this.currentPage() + 1) * this.pageSize, this.totalElements()));
  searchQuery = '';
  filterRubroId = '';
  filterActivo = '';
  private searchTimeout?: ReturnType<typeof setTimeout>;

  // Main modal
  modalOpen = signal(false);
  saving = signal(false);
  formError = signal<string | null>(null);
  editingId: number | null = null;
  form: ParamRubroRequest = {
    codigo: '', rubroId: 0, demandaBaseTrim: 0, precioReferencia: 0,
    elasticidadPrecio: 0, elasticidadMarketing: 0, elasticidadCalidad: 0,
    pesoPrecio: 0, pesoMarketing: 0, pesoCalidad: 0, pesoMarca: 0,
    costoUnitMp: 0, pctMpImportada: 0, costosFijosTrim: 0,
    depreciacionTrim: 0, costoExpansionCapacidad: 0,
    salarioPromedioSector: 0, productividadEmpleado: 0,
    brandEquityInicial: 0, decaimientoBe: 0, spreadTasa: 0,
  };

  // Trimestres modal
  trimModalOpen = signal(false);
  trimSaving = signal(false);
  trimError = signal<string | null>(null);
  trimSetId: number | null = null;
  trimSetCode = '';
  trimestres: RubroTrimestreDto[] = [];

  ngOnInit(): void {
    this.api.listRubros(0, 100).subscribe(res => this.rubros.set(res.content));
    this.loadPage(0);
  }

  loadPage(page: number): void {
    this.loading.set(true);
    const activo = this.filterActivo === '' ? undefined : this.filterActivo === 'true';
    const rubroId = this.filterRubroId ? +this.filterRubroId : undefined;
    this.api.listParamRubro(page, this.pageSize, this.searchQuery || undefined, rubroId, activo).subscribe({
      next: (res) => { this.rows.set(res.content); this.currentPage.set(res.page); this.totalElements.set(res.totalElements); this.totalPages.set(res.totalPages); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  onSearch(): void { clearTimeout(this.searchTimeout); this.searchTimeout = setTimeout(() => this.loadPage(0), 300); }
  toggleActivo(r: ParamRubroRow): void { this.api.toggleParamRubroActivo(r.id, !r.activo).subscribe(() => this.loadPage(this.currentPage())); }

  openCreate(): void {
    this.editingId = null;
    this.form = {
      codigo: '', rubroId: this.rubros()[0]?.id ?? 0, demandaBaseTrim: 0, precioReferencia: 0,
      elasticidadPrecio: 1.5, elasticidadMarketing: 0.5, elasticidadCalidad: 0.4,
      pesoPrecio: 0.4, pesoMarketing: 0.3, pesoCalidad: 0.2, pesoMarca: 0.1,
      costoUnitMp: 0, pctMpImportada: 0, costosFijosTrim: 0,
      depreciacionTrim: 0, costoExpansionCapacidad: 0,
      salarioPromedioSector: 0, productividadEmpleado: 0,
      brandEquityInicial: 0.5, decaimientoBe: 0.05, spreadTasa: 0.05,
    };
    this.formError.set(null); this.modalOpen.set(true);
  }

  openEdit(r: ParamRubroRow): void {
    this.editingId = r.id;
    this.api.getParamRubro(r.id).subscribe(d => {
      this.form = {
        codigo: d.codigo, rubroId: d.rubroId, demandaBaseTrim: d.demandaBaseTrim,
        precioReferencia: d.precioReferencia, elasticidadPrecio: d.elasticidadPrecio,
        elasticidadMarketing: d.elasticidadMarketing, elasticidadCalidad: d.elasticidadCalidad,
        pesoPrecio: d.pesoPrecio, pesoMarketing: d.pesoMarketing, pesoCalidad: d.pesoCalidad,
        pesoMarca: d.pesoMarca, costoUnitMp: d.costoUnitMp, pctMpImportada: d.pctMpImportada,
        costosFijosTrim: d.costosFijosTrim, depreciacionTrim: d.depreciacionTrim,
        costoExpansionCapacidad: d.costoExpansionCapacidad, salarioPromedioSector: d.salarioPromedioSector,
        productividadEmpleado: d.productividadEmpleado, brandEquityInicial: d.brandEquityInicial,
        decaimientoBe: d.decaimientoBe, spreadTasa: d.spreadTasa,
      };
      this.formError.set(null); this.modalOpen.set(true);
    });
  }

  closeModal(): void { this.modalOpen.set(false); }

  save(): void {
    if (!this.form.codigo || !this.form.rubroId) { this.formError.set('Código y rubro son obligatorios'); return; }
    this.saving.set(true);
    const obs = this.editingId ? this.api.updateParamRubro(this.editingId, this.form) : this.api.createParamRubro(this.form);
    obs.subscribe({
      next: () => { this.saving.set(false); this.closeModal(); this.loadPage(this.editingId ? this.currentPage() : 0); },
      error: (err) => { this.saving.set(false); this.formError.set(err.error?.detail ?? 'Error al guardar'); },
    });
  }

  // Trimestres
  openTrimestres(r: ParamRubroRow): void {
    this.trimSetId = r.id;
    this.trimSetCode = r.codigo;
    this.trimError.set(null);
    this.api.getParamRubroTrimestres(r.id).subscribe(ts => {
      this.trimestres = ts.length ? ts : [{ trimestre: 1, estacionalidad: 1.0 }];
      this.trimModalOpen.set(true);
    });
  }

  closeTrimModal(): void { this.trimModalOpen.set(false); }
  addTrimestre(): void {
    const next = this.trimestres.length + 1;
    this.trimestres = [...this.trimestres, { trimestre: next, estacionalidad: 1.0 }];
  }

  saveTrimestres(): void {
    if (!this.trimSetId) return;
    this.trimSaving.set(true);
    this.api.replaceParamRubroTrimestres(this.trimSetId, this.trimestres).subscribe({
      next: (ts) => { this.trimestres = ts; this.trimSaving.set(false); this.closeTrimModal(); },
      error: (err) => { this.trimSaving.set(false); this.trimError.set(err.error?.detail ?? 'Error al guardar'); },
    });
  }
}

import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  Calendar,
  ChevronLeft,
  ChevronRight,
  LucideAngularModule,
  Pencil,
  Plus,
  Search,
  ToggleLeft,
  ToggleRight,
  X,
} from 'lucide-angular';
import {
  MacroTrimestreDto,
  ParamMacroRequest,
  ParamMacroRow,
} from '../../../core/models/admin.model';
import { AdminApiService } from '../../../core/services/admin-api.service';

@Component({
  selector: 'app-admin-parametros-macro',
  standalone: true,
  imports: [FormsModule, LucideAngularModule],
  templateUrl: './parametros-macro.component.html',
})
export class ParametrosMacroAdminComponent implements OnInit {
  private api = inject(AdminApiService);
  readonly icons = {
    Plus,
    Search,
    ChevronLeft,
    ChevronRight,
    Pencil,
    ToggleLeft,
    ToggleRight,
    X,
    Calendar,
  };

  readonly pageSize = 10;
  rows = signal<ParamMacroRow[]>([]);
  loading = signal(true);
  currentPage = signal(0);
  totalElements = signal(0);
  totalPages = signal(0);
  rangeStart = computed(() =>
    this.totalElements() === 0 ? 0 : this.currentPage() * this.pageSize + 1,
  );
  rangeEnd = computed(() =>
    Math.min((this.currentPage() + 1) * this.pageSize, this.totalElements()),
  );
  searchQuery = '';
  filterActivo = '';
  private searchTimeout?: ReturnType<typeof setTimeout>;

  // Main modal
  modalOpen = signal(false);
  saving = signal(false);
  formError = signal<string | null>(null);
  editingId: number | null = null;
  form = {
    nombreSet: '',
    vigenteDesde: '',
    salarioMinimoQ1: 0,
    salarioMinimoQ4: 0,
    ipsPatronal: 0,
    ipsTrabajador: 0,
    aguinaldoFactor: 0,
    tasaIre: 0,
    ivaGeneral: 0,
  };

  // Trimestres modal
  trimModalOpen = signal(false);
  trimSaving = signal(false);
  trimError = signal<string | null>(null);
  trimSetId: number | null = null;
  trimSetName = '';
  trimestres: MacroTrimestreDto[] = [];

  ngOnInit(): void {
    this.loadPage(0);
  }

  loadPage(page: number): void {
    this.loading.set(true);
    const activo = this.filterActivo === '' ? undefined : this.filterActivo === 'true';
    this.api.listParamMacro(page, this.pageSize, this.searchQuery || undefined, activo).subscribe({
      next: (res) => {
        this.rows.set(res.content);
        this.currentPage.set(res.page);
        this.totalElements.set(res.totalElements);
        this.totalPages.set(res.totalPages);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onSearch(): void {
    clearTimeout(this.searchTimeout);
    this.searchTimeout = setTimeout(() => this.loadPage(0), 300);
  }
  toggleActivo(r: ParamMacroRow): void {
    this.api
      .toggleParamMacroActivo(r.id, !r.activo)
      .subscribe(() => this.loadPage(this.currentPage()));
  }

  openCreate(): void {
    this.editingId = null;
    this.form = {
      nombreSet: '',
      vigenteDesde: '',
      salarioMinimoQ1: 0,
      salarioMinimoQ4: 0,
      ipsPatronal: 0,
      ipsTrabajador: 0,
      aguinaldoFactor: 0,
      tasaIre: 0,
      ivaGeneral: 0,
    };
    this.formError.set(null);
    this.modalOpen.set(true);
  }

  openEdit(r: ParamMacroRow): void {
    this.editingId = r.id;
    this.api.getParamMacro(r.id).subscribe((d) => {
      this.form = {
        nombreSet: d.nombreSet,
        vigenteDesde: d.vigenteDesde ?? '',
        salarioMinimoQ1: d.salarioMinimoQ1,
        salarioMinimoQ4: d.salarioMinimoQ4,
        ipsPatronal: d.ipsPatronal,
        ipsTrabajador: d.ipsTrabajador,
        aguinaldoFactor: d.aguinaldoFactor,
        tasaIre: d.tasaIre,
        ivaGeneral: d.ivaGeneral,
      };
      this.formError.set(null);
      this.modalOpen.set(true);
    });
  }

  closeModal(): void {
    this.modalOpen.set(false);
  }

  save(): void {
    if (!this.form.nombreSet) {
      this.formError.set('Nombre del set es obligatorio');
      return;
    }
    this.saving.set(true);
    const req: ParamMacroRequest = {
      nombreSet: this.form.nombreSet,
      vigenteDesde: this.form.vigenteDesde || undefined,
      salarioMinimoQ1: this.form.salarioMinimoQ1,
      salarioMinimoQ4: this.form.salarioMinimoQ4,
      ipsPatronal: this.form.ipsPatronal,
      ipsTrabajador: this.form.ipsTrabajador,
      aguinaldoFactor: this.form.aguinaldoFactor,
      tasaIre: this.form.tasaIre,
      ivaGeneral: this.form.ivaGeneral,
    };
    const obs = this.editingId
      ? this.api.updateParamMacro(this.editingId, req)
      : this.api.createParamMacro(req);
    obs.subscribe({
      next: () => {
        this.saving.set(false);
        this.closeModal();
        this.loadPage(this.editingId ? this.currentPage() : 0);
      },
      error: (err) => {
        this.saving.set(false);
        this.formError.set(err.error?.detail ?? 'Error al guardar');
      },
    });
  }

  // Trimestres
  openTrimestres(r: ParamMacroRow): void {
    this.trimSetId = r.id;
    this.trimSetName = r.nombreSet;
    this.trimError.set(null);
    this.api.getParamMacroTrimestres(r.id).subscribe((ts) => {
      this.trimestres = ts.length
        ? ts
        : [{ trimestre: 1, inflacionTrim: 0, tipoCambio: 0, tpmAnual: 0 }];
      this.trimModalOpen.set(true);
    });
  }

  closeTrimModal(): void {
    this.trimModalOpen.set(false);
  }

  addTrimestre(): void {
    const next = this.trimestres.length + 1;
    this.trimestres = [
      ...this.trimestres,
      { trimestre: next, inflacionTrim: 0, tipoCambio: 0, tpmAnual: 0 },
    ];
  }

  saveTrimestres(): void {
    if (!this.trimSetId) return;
    this.trimSaving.set(true);
    this.api.replaceParamMacroTrimestres(this.trimSetId, this.trimestres).subscribe({
      next: (ts) => {
        this.trimestres = ts;
        this.trimSaving.set(false);
        this.closeTrimModal();
      },
      error: (err) => {
        this.trimSaving.set(false);
        this.trimError.set(err.error?.detail ?? 'Error al guardar trimestres');
      },
    });
  }
}

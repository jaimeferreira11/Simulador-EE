import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Plus, Search, ChevronLeft, ChevronRight, Pencil, ToggleLeft, ToggleRight, X } from 'lucide-angular';
import { AdminApiService } from '../../../core/services/admin-api.service';
import { RubroRow, RubroRequest } from '../../../core/models/admin.model';

@Component({
  selector: 'app-admin-rubros',
  standalone: true,
  imports: [FormsModule, LucideAngularModule],
  templateUrl: './rubros.component.html',
})
export class RubrosAdminComponent implements OnInit {
  private api = inject(AdminApiService);
  readonly icons = { Plus, Search, ChevronLeft, ChevronRight, Pencil, ToggleLeft, ToggleRight, X };

  readonly pageSize = 10;
  rows = signal<RubroRow[]>([]);
  loading = signal(true);
  currentPage = signal(0);
  totalElements = signal(0);
  totalPages = signal(0);
  rangeStart = computed(() => this.totalElements() === 0 ? 0 : this.currentPage() * this.pageSize + 1);
  rangeEnd = computed(() => Math.min((this.currentPage() + 1) * this.pageSize, this.totalElements()));
  searchQuery = '';
  filterActivo = '';
  private searchTimeout?: ReturnType<typeof setTimeout>;

  modalOpen = signal(false);
  saving = signal(false);
  formError = signal<string | null>(null);
  editingId: number | null = null;
  form = { codigo: '', nombre: '', descripcion: '' };

  ngOnInit(): void { this.loadPage(0); }

  loadPage(page: number): void {
    this.loading.set(true);
    const activo = this.filterActivo === '' ? undefined : this.filterActivo === 'true';
    this.api.listRubros(page, this.pageSize, this.searchQuery || undefined, activo).subscribe({
      next: (res) => { this.rows.set(res.content); this.currentPage.set(res.page); this.totalElements.set(res.totalElements); this.totalPages.set(res.totalPages); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  onSearch(): void { clearTimeout(this.searchTimeout); this.searchTimeout = setTimeout(() => this.loadPage(0), 300); }

  toggleActivo(r: RubroRow): void { this.api.toggleRubroActivo(r.id, !r.activo).subscribe(() => this.loadPage(this.currentPage())); }

  openCreate(): void { this.editingId = null; this.form = { codigo: '', nombre: '', descripcion: '' }; this.formError.set(null); this.modalOpen.set(true); }

  openEdit(r: RubroRow): void {
    this.editingId = r.id;
    this.api.getRubro(r.id).subscribe(d => {
      this.form = { codigo: d.codigo, nombre: d.nombre, descripcion: d.descripcion ?? '' };
      this.formError.set(null);
      this.modalOpen.set(true);
    });
  }

  closeModal(): void { this.modalOpen.set(false); }

  save(): void {
    if (!this.form.codigo || !this.form.nombre) { this.formError.set('Código y nombre son obligatorios'); return; }
    this.saving.set(true);
    const req: RubroRequest = { codigo: this.form.codigo, nombre: this.form.nombre, descripcion: this.form.descripcion || undefined };
    const obs = this.editingId ? this.api.updateRubro(this.editingId, req) : this.api.createRubro(req);
    obs.subscribe({
      next: () => { this.saving.set(false); this.closeModal(); this.loadPage(this.editingId ? this.currentPage() : 0); },
      error: (err) => { this.saving.set(false); this.formError.set(err.error?.detail ?? 'Error al guardar'); },
    });
  }
}

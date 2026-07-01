import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Plus, Search, ChevronLeft, ChevronRight, Pencil, ToggleLeft, ToggleRight, X } from 'lucide-angular';
import { AdminApiService } from '../../../core/services/admin-api.service';
import { EntidadRow, EntidadRequest } from '../../../core/models/admin.model';

@Component({
  selector: 'app-admin-entidades',
  standalone: true,
  imports: [FormsModule, LucideAngularModule],
  templateUrl: './entidades.component.html',
})
export class EntidadesAdminComponent implements OnInit {
  private api = inject(AdminApiService);
  readonly icons = { Plus, Search, ChevronLeft, ChevronRight, Pencil, ToggleLeft, ToggleRight, X };

  readonly pageSize = 10;
  rows = signal<EntidadRow[]>([]);
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
  form: EntidadRequest = { nombre: '', tipo: 'UNIVERSIDAD', descripcion: '', contactoNombre: '', contactoEmail: '' };

  ngOnInit(): void { this.loadPage(0); }

  loadPage(page: number): void {
    this.loading.set(true);
    const activa = this.filterActivo === '' ? undefined : this.filterActivo === 'true';
    this.api.listEntidades(page, this.pageSize, this.searchQuery || undefined, activa).subscribe({
      next: (res) => { this.rows.set(res.content); this.currentPage.set(res.page); this.totalElements.set(res.totalElements); this.totalPages.set(res.totalPages); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  onSearch(): void { clearTimeout(this.searchTimeout); this.searchTimeout = setTimeout(() => this.loadPage(0), 300); }
  toggleActiva(e: EntidadRow): void { this.api.toggleEntidadActiva(e.id, !e.activa).subscribe(() => this.loadPage(this.currentPage())); }

  openCreate(): void {
    this.editingId = null;
    this.form = { nombre: '', tipo: 'UNIVERSIDAD', descripcion: '', contactoNombre: '', contactoEmail: '' };
    this.formError.set(null); this.modalOpen.set(true);
  }

  openEdit(e: EntidadRow): void {
    this.editingId = e.id;
    this.api.getEntidad(e.id).subscribe(d => {
      this.form = { nombre: d.nombre, tipo: d.tipo, descripcion: d.descripcion ?? '', contactoNombre: d.contactoNombre ?? '', contactoEmail: d.contactoEmail ?? '' };
      this.formError.set(null); this.modalOpen.set(true);
    });
  }

  closeModal(): void { this.modalOpen.set(false); }

  save(): void {
    if (!this.form.nombre || !this.form.tipo) { this.formError.set('Nombre y tipo son obligatorios'); return; }
    this.saving.set(true);
    const obs = this.editingId ? this.api.updateEntidad(this.editingId, this.form) : this.api.createEntidad(this.form);
    obs.subscribe({
      next: () => { this.saving.set(false); this.closeModal(); this.loadPage(this.editingId ? this.currentPage() : 0); },
      error: (err) => { this.saving.set(false); this.formError.set(err.error?.detail ?? 'Error al guardar'); },
    });
  }
}

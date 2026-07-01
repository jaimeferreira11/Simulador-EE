import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Plus, Search, ChevronLeft, ChevronRight, Pencil, ToggleLeft, ToggleRight, X } from 'lucide-angular';
import { AdminApiService } from '../../../core/services/admin-api.service';
import { EventoRow, EventoRequest, RubroRow } from '../../../core/models/admin.model';

@Component({
  selector: 'app-admin-eventos',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule],
  templateUrl: './eventos.component.html',
})
export class EventosAdminComponent implements OnInit {
  private api = inject(AdminApiService);
  readonly icons = { Plus, Search, ChevronLeft, ChevronRight, Pencil, ToggleLeft, ToggleRight, X };

  readonly pageSize = 10;
  rows = signal<EventoRow[]>([]);
  rubros = signal<RubroRow[]>([]);
  loading = signal(true);
  currentPage = signal(0);
  totalElements = signal(0);
  totalPages = signal(0);
  rangeStart = computed(() => this.totalElements() === 0 ? 0 : this.currentPage() * this.pageSize + 1);
  rangeEnd = computed(() => Math.min((this.currentPage() + 1) * this.pageSize, this.totalElements()));
  searchQuery = '';
  filterRubroId = '';
  filterSeveridad = '';
  filterActivo = '';
  private searchTimeout?: ReturnType<typeof setTimeout>;

  modalOpen = signal(false);
  saving = signal(false);
  formError = signal<string | null>(null);
  editingId: number | null = null;
  confirmTarget = signal<EventoRow | null>(null);
  form: EventoRequest = {
    codigo: '', nombre: '', descripcion: '', severidad: 'MODERADO', tipoEfecto: 'COSTO_MP',
    magnitudDefault: 0, duracionQ: 1, requiereAnuncioPrevio: false,
    overridePesoPrecio: null, overridePesoMarketing: null, overridePesoCalidad: null, overridePesoMarca: null,
    rubroId: null,
  };

  overridePesosTouched = computed(() => {
    return [
      this.form.overridePesoPrecio,
      this.form.overridePesoMarketing,
      this.form.overridePesoCalidad,
      this.form.overridePesoMarca,
    ].some(v => v !== null && v !== undefined && !Number.isNaN(Number(v)));
  });

  overridePesosSum = computed(() => {
    return (Number(this.form.overridePesoPrecio) || 0)
         + (Number(this.form.overridePesoMarketing) || 0)
         + (Number(this.form.overridePesoCalidad) || 0)
         + (Number(this.form.overridePesoMarca) || 0);
  });

  overridePesosValid = computed(() => Math.abs(this.overridePesosSum() - 1) < 0.001);

  ngOnInit(): void {
    this.api.listRubros(0, 100).subscribe(res => this.rubros.set(res.content));
    this.loadPage(0);
  }

  loadPage(page: number): void {
    this.loading.set(true);
    const activo = this.filterActivo === '' ? undefined : this.filterActivo === 'true';
    const rubroId = this.filterRubroId ? +this.filterRubroId : undefined;
    const severidad = this.filterSeveridad || undefined;
    this.api.listEventos(page, this.pageSize, this.searchQuery || undefined, rubroId, severidad, activo).subscribe({
      next: (res) => { this.rows.set(res.content); this.currentPage.set(res.page); this.totalElements.set(res.totalElements); this.totalPages.set(res.totalPages); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  onSearch(): void { clearTimeout(this.searchTimeout); this.searchTimeout = setTimeout(() => this.loadPage(0), 300); }

  askToggle(e: EventoRow): void { this.confirmTarget.set(e); }
  cancelToggle(): void { this.confirmTarget.set(null); }
  confirmToggle(): void {
    const e = this.confirmTarget();
    if (!e) return;
    this.api.toggleEventoActivo(e.id, !e.activo).subscribe(() => {
      this.confirmTarget.set(null);
      this.loadPage(this.currentPage());
    });
  }

  severidadClass(s: string): string {
    switch (s) {
      case 'LEVE': return 'bg-blue-50 text-blue-700';
      case 'MODERADO': return 'bg-yellow-50 text-yellow-700';
      case 'GRAVE': return 'bg-red-50 text-red-700';
      case 'POSITIVO': return 'bg-green-50 text-green-700';
      default: return 'bg-gray-50 text-gray-700';
    }
  }

  openCreate(): void {
    this.editingId = null;
    this.form = { codigo: '', nombre: '', descripcion: '', severidad: 'MODERADO', tipoEfecto: 'COSTO_MP', magnitudDefault: 0, duracionQ: 1, requiereAnuncioPrevio: false, overridePesoPrecio: null, overridePesoMarketing: null, overridePesoCalidad: null, overridePesoMarca: null, rubroId: null };
    this.formError.set(null); this.modalOpen.set(true);
  }

  openEdit(e: EventoRow): void {
    this.editingId = e.id;
    this.api.getEvento(e.id).subscribe(d => {
      this.form = {
        codigo: d.codigo, nombre: d.nombre, descripcion: d.descripcion ?? '', severidad: d.severidad,
        tipoEfecto: d.tipoEfecto, magnitudDefault: d.magnitudDefault, duracionQ: d.duracionQ,
        requiereAnuncioPrevio: d.requiereAnuncioPrevio,
        overridePesoPrecio: d.overridePesoPrecio, overridePesoMarketing: d.overridePesoMarketing,
        overridePesoCalidad: d.overridePesoCalidad, overridePesoMarca: d.overridePesoMarca,
        rubroId: d.rubroId,
      };
      this.formError.set(null); this.modalOpen.set(true);
    });
  }

  closeModal(): void { this.modalOpen.set(false); }

  save(): void {
    const codigo = (this.form.codigo ?? '').trim().toUpperCase();
    this.form.codigo = codigo;
    if (!codigo || !this.form.nombre?.trim()) {
      this.formError.set('Código y nombre son obligatorios');
      return;
    }
    if (!/^[A-Z][A-Z0-9_]{1,59}$/.test(codigo)) {
      this.formError.set('Código inválido. Debe empezar con letra y usar solo MAYÚSCULAS, números o guion bajo.');
      return;
    }
    if (this.form.duracionQ < 1) {
      this.formError.set('La duración debe ser al menos 1 trimestre.');
      return;
    }
    if (this.overridePesosTouched() && !this.overridePesosValid()) {
      this.formError.set('Los pesos override deben completarse los 4 y sumar 1.00 (o dejar todos vacíos).');
      return;
    }
    this.saving.set(true);
    const obs = this.editingId ? this.api.updateEvento(this.editingId, this.form) : this.api.createEvento(this.form);
    obs.subscribe({
      next: () => { this.saving.set(false); this.closeModal(); this.loadPage(this.editingId ? this.currentPage() : 0); },
      error: (err) => { this.saving.set(false); this.formError.set(err.error?.detail ?? 'Error al guardar'); },
    });
  }
}

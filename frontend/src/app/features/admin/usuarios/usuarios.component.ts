import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Plus, Search, ChevronLeft, ChevronRight, Pencil, ToggleLeft, ToggleRight, X } from 'lucide-angular';
import { AdminApiService } from '../../../core/services/admin-api.service';
import { UsuarioRow, UsuarioDetail, UsuarioCreateRequest, UsuarioUpdateRequest } from '../../../core/models/admin.model';
import { Rol } from '../../../core/models/usuario.model';

@Component({
  selector: 'app-admin-usuarios',
  standalone: true,
  imports: [FormsModule, LucideAngularModule],
  templateUrl: './usuarios.component.html',
})
export class UsuariosAdminComponent implements OnInit {
  private api = inject(AdminApiService);
  readonly icons = { Plus, Search, ChevronLeft, ChevronRight, Pencil, ToggleLeft, ToggleRight, X };

  readonly pageSize = 10;
  rows = signal<UsuarioRow[]>([]);
  loading = signal(true);
  currentPage = signal(0);
  totalElements = signal(0);
  totalPages = signal(0);
  rangeStart = computed(() => this.totalElements() === 0 ? 0 : this.currentPage() * this.pageSize + 1);
  rangeEnd = computed(() => Math.min((this.currentPage() + 1) * this.pageSize, this.totalElements()));

  searchQuery = '';
  filterRol = '';
  filterActivo = '';
  private searchTimeout?: ReturnType<typeof setTimeout>;

  // Modal
  modalOpen = signal(false);
  saving = signal(false);
  formError = signal<string | null>(null);
  editingId: number | null = null;
  form = { email: '', nombreCompleto: '', rolCodigo: 'MODERADOR', password: '' };

  ngOnInit(): void {
    this.loadPage(0);
  }

  loadPage(page: number): void {
    this.loading.set(true);
    const activo = this.filterActivo === '' ? undefined : this.filterActivo === 'true';
    const rol = this.filterRol || undefined;
    this.api.listUsuarios(page, this.pageSize, this.searchQuery || undefined, rol, activo).subscribe({
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

  rolLabel(rol: Rol): string {
    switch (rol) {
      case 'ADMIN_PLATAFORMA': return 'Admin';
      case 'MODERADOR': return 'Moderador';
      case 'JUGADOR': return 'Jugador';
    }
  }

  rolClass(rol: Rol): string {
    switch (rol) {
      case 'ADMIN_PLATAFORMA': return 'bg-red-50 text-red-700';
      case 'MODERADOR': return 'bg-blue-50 text-blue-700';
      case 'JUGADOR': return 'bg-purple-50 text-purple-700';
    }
  }

  toggleActivo(u: UsuarioRow): void {
    this.api.toggleUsuarioActivo(u.id, !u.activo).subscribe(() => this.loadPage(this.currentPage()));
  }

  openCreate(): void {
    this.editingId = null;
    this.form = { email: '', nombreCompleto: '', rolCodigo: 'MODERADOR', password: '' };
    this.formError.set(null);
    this.modalOpen.set(true);
  }

  openEdit(u: UsuarioRow): void {
    this.editingId = u.id;
    this.form = { email: u.email, nombreCompleto: u.nombreCompleto, rolCodigo: u.rol, password: '' };
    this.formError.set(null);
    this.modalOpen.set(true);
  }

  closeModal(): void {
    this.modalOpen.set(false);
  }

  save(): void {
    this.formError.set(null);
    this.saving.set(true);

    if (this.editingId) {
      const req: UsuarioUpdateRequest = {
        nombreCompleto: this.form.nombreCompleto,
        rolCodigo: this.form.rolCodigo,
      };
      if (this.form.password) req.password = this.form.password;
      this.api.updateUsuario(this.editingId, req).subscribe({
        next: () => { this.saving.set(false); this.closeModal(); this.loadPage(this.currentPage()); },
        error: (err) => { this.saving.set(false); this.formError.set(err.error?.detail ?? 'Error al guardar'); },
      });
    } else {
      if (!this.form.email || !this.form.password || !this.form.nombreCompleto) {
        this.saving.set(false);
        this.formError.set('Todos los campos son obligatorios');
        return;
      }
      const req: UsuarioCreateRequest = {
        email: this.form.email,
        password: this.form.password,
        nombreCompleto: this.form.nombreCompleto,
        rolCodigo: this.form.rolCodigo,
      };
      this.api.createUsuario(req).subscribe({
        next: () => { this.saving.set(false); this.closeModal(); this.loadPage(0); },
        error: (err) => { this.saving.set(false); this.formError.set(err.error?.detail ?? 'Error al crear'); },
      });
    }
  }
}

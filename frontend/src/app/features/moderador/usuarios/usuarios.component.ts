import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  LucideAngularModule,
  Plus,
  Search,
  ChevronLeft,
  ChevronRight,
  X,
  Mail,
  Upload,
  FileText,
  Download,
  Check,
  CircleAlert,
} from 'lucide-angular';
import { HotToastService } from '@ngxpert/hot-toast';
import { UsuarioApiService, ImportResult } from '../../../core/services/usuario-api.service';
import { Usuario } from '../../../core/models/usuario.model';

@Component({
  selector: 'app-moderador-usuarios',
  standalone: true,
  imports: [FormsModule, LucideAngularModule],
  templateUrl: './usuarios.component.html',
})
export class UsuariosModComponent implements OnInit, OnDestroy {
  private api = inject(UsuarioApiService);
  private toast = inject(HotToastService);
  readonly icons = {
    Plus,
    Search,
    ChevronLeft,
    ChevronRight,
    X,
    Mail,
    Upload,
    FileText,
    Download,
    Check,
    CircleAlert,
  };

  readonly pageSize = 10;
  rows = signal<Usuario[]>([]);
  loading = signal(true);
  currentPage = signal(0);
  totalElements = signal(0);
  totalPages = computed(() => Math.max(1, Math.ceil(this.totalElements() / this.pageSize)));
  rangeStart = computed(() => (this.totalElements() === 0 ? 0 : this.currentPage() * this.pageSize + 1));
  rangeEnd = computed(() => Math.min((this.currentPage() + 1) * this.pageSize, this.totalElements()));

  searchQuery = '';
  // El moderador gestiona jugadores: el filtro de rol queda fijo en JUGADOR.
  private readonly rolFiltro = 'JUGADOR';
  private searchTimeout?: ReturnType<typeof setTimeout>;

  // Modal de creacion (solo JUGADOR, sin selector de rol ni contrasena)
  modalOpen = signal(false);
  saving = signal(false);
  formError = signal<string | null>(null);
  form = { email: '', nombreCompleto: '' };

  // Modal de carga masiva (CSV). El backend procesa al subir: subida -> resultados.
  importModalOpen = signal(false);
  importing = signal(false);
  importError = signal<string | null>(null);
  selectedFile = signal<File | null>(null);
  importResult = signal<ImportResult | null>(null);

  ngOnDestroy(): void {
    if (this.searchTimeout) clearTimeout(this.searchTimeout);
  }

  ngOnInit(): void {
    this.loadPage(0);
  }

  loadPage(page: number): void {
    this.loading.set(true);
    this.api.list(page, this.pageSize, this.rolFiltro, undefined, this.searchQuery || undefined).subscribe({
      next: (res) => {
        this.rows.set(res.content);
        this.currentPage.set(res.page);
        this.totalElements.set(res.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  onSearch(): void {
    clearTimeout(this.searchTimeout);
    this.searchTimeout = setTimeout(() => this.loadPage(0), 300);
  }

  openCreate(): void {
    this.form = { email: '', nombreCompleto: '' };
    this.formError.set(null);
    this.modalOpen.set(true);
  }

  closeModal(): void {
    this.modalOpen.set(false);
  }

  save(): void {
    this.formError.set(null);

    if (!this.form.email.trim() || !this.form.nombreCompleto.trim()) {
      this.formError.set('Email y nombre completo son obligatorios');
      return;
    }

    this.saving.set(true);
    const email = this.form.email.trim();
    this.api
      .create({
        email,
        nombre_completo: this.form.nombreCompleto.trim(),
        rol_codigo: 'JUGADOR',
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.closeModal();
          this.toast.success(
            `Se envió un email a ${email} para que defina su contraseña.`,
            {
              duration: 5000,
              style: { background: '#065f46', color: '#fff' },
              iconTheme: { primary: '#34d399', secondary: '#fff' },
            },
          );
          this.loadPage(0);
        },
        error: (err) => {
          this.saving.set(false);
          this.formError.set(err.error?.detail ?? 'No se pudo crear el jugador');
        },
      });
  }

  // --- Carga masiva CSV ---

  openImport(): void {
    this.selectedFile.set(null);
    this.importResult.set(null);
    this.importError.set(null);
    this.importing.set(false);
    this.importModalOpen.set(true);
  }

  closeImport(): void {
    this.importModalOpen.set(false);
    // Refrescar la lista al cerrar para reflejar los jugadores recien creados.
    if (this.importResult()) {
      this.loadPage(0);
    }
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.selectedFile.set(file);
    this.importError.set(null);
  }

  downloadTemplate(): void {
    const blob = new Blob(['email,nombre_completo\n'], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'plantilla_jugadores.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  uploadCsv(): void {
    const file = this.selectedFile();
    if (!file) {
      this.importError.set('Seleccioná un archivo CSV.');
      return;
    }
    this.importError.set(null);
    this.importing.set(true);
    this.api.importCsv(file).subscribe({
      next: (res) => {
        this.importing.set(false);
        this.importResult.set(res);
        if (res.errores.length === 0) {
          this.toast.success(
            `Se importaron ${res.creados.length} jugadores correctamente.`,
            {
              duration: 5000,
              style: { background: '#065f46', color: '#fff' },
              iconTheme: { primary: '#34d399', secondary: '#fff' },
            },
          );
        }
      },
      error: (err) => {
        this.importing.set(false);
        this.importError.set(
          err.error?.detail ?? 'No se pudo procesar el archivo. Intentá nuevamente.',
        );
      },
    });
  }
}

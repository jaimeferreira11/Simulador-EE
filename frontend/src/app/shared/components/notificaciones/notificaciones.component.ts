import { Component, inject, signal, OnInit, HostListener, ElementRef, ViewChild } from '@angular/core';
import { LucideAngularModule, Bell, Info, AlertTriangle, AlertOctagon, CheckCheck } from 'lucide-angular';
import { NotificacionApiService } from '../../../core/services/notificacion-api.service';
import { Notificacion, SeveridadNotificacion } from '../../../core/models/notificacion.model';

@Component({
  selector: 'app-notificaciones',
  standalone: true,
  imports: [LucideAngularModule],
  template: `
    <div class="relative">
      <!-- Bell button -->
      <button #bellBtn (click)="toggle()" class="relative p-1.5 rounded-md hover:bg-border/50 transition-colors">
        <lucide-icon [img]="icons.Bell" [size]="20" class="text-text-icon"></lucide-icon>
        @if (notifService.noLeidas() > 0) {
          <span class="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] px-1 rounded-full bg-red-500 text-white text-[10px] font-bold flex items-center justify-center">
            {{ notifService.noLeidas() > 99 ? '99+' : notifService.noLeidas() }}
          </span>
        }
      </button>

      <!-- Dropdown -->
      @if (open()) {
        <div class="fixed w-80 bg-white rounded-lg shadow-lg border border-border z-[9999] overflow-hidden"
             [style.top.px]="dropdownTop" [style.left.px]="dropdownLeft">
          <!-- Header -->
          <div class="flex items-center justify-between px-4 py-3 border-b border-border">
            <span class="font-display text-sm text-text-primary">Notificaciones</span>
            @if (notifService.noLeidas() > 0) {
              <button (click)="marcarTodas()" class="flex items-center gap-1 text-xs text-primary font-body hover:underline">
                <lucide-icon [img]="icons.CheckCheck" [size]="12"></lucide-icon>
                Marcar todas
              </button>
            }
          </div>

          <!-- List -->
          <div class="max-h-80 overflow-y-auto sim-scrollbar">
            @if (loading()) {
              <div class="flex items-center justify-center py-8">
                <span class="text-text-secondary font-body text-xs">Cargando...</span>
              </div>
            } @else if (notificaciones().length === 0) {
              <div class="flex flex-col items-center justify-center py-8 gap-2">
                <lucide-icon [img]="icons.Bell" [size]="24" class="text-text-secondary/30"></lucide-icon>
                <span class="text-text-secondary font-body text-xs">Sin notificaciones</span>
              </div>
            } @else {
              @for (n of notificaciones(); track n.id) {
                <button
                  (click)="marcarLeida(n)"
                  class="w-full flex items-start gap-3 px-4 py-3 hover:bg-surface-card transition-colors text-left border-b border-border/30 last:border-0"
                  [class.bg-blue-50]="!n.leida"
                >
                  <!-- Severity icon -->
                  <div class="shrink-0 mt-0.5">
                    <lucide-icon [img]="severidadIcon(n.severidad)" [size]="16"
                                 [class]="severidadColor(n.severidad)"></lucide-icon>
                  </div>
                  <!-- Content -->
                  <div class="flex-1 min-w-0">
                    <p class="font-body text-xs text-text-primary leading-snug"
                       [class.font-semibold]="!n.leida">
                      {{ n.titulo }}
                    </p>
                    <span class="font-body text-[10px] text-text-secondary mt-0.5 block">
                      {{ tiempoRelativo(n.created_at) }}
                    </span>
                  </div>
                  <!-- Unread dot -->
                  @if (!n.leida) {
                    <span class="w-2 h-2 rounded-full bg-primary shrink-0 mt-1.5"></span>
                  }
                </button>
              }
            }
          </div>
        </div>
      }
    </div>
  `,
})
export class NotificacionesComponent implements OnInit {
  readonly icons = { Bell, Info, AlertTriangle, AlertOctagon, CheckCheck };

  notifService = inject(NotificacionApiService);

  @ViewChild('bellBtn', { static: false }) bellBtn!: ElementRef<HTMLButtonElement>;

  open = signal(false);
  loading = signal(false);
  notificaciones = signal<Notificacion[]>([]);
  dropdownTop = 0;
  dropdownLeft = 0;

  ngOnInit(): void {
    // Real-time updates arrive via WebSocket (notificacion.nueva).
    // This call only seeds the unread count and ultimaNotifId once.
    this.notifService.inicializar();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (!target.closest('app-notificaciones')) {
      this.open.set(false);
    }
  }

  toggle(): void {
    const isOpen = !this.open();
    if (isOpen && this.bellBtn) {
      const rect = this.bellBtn.nativeElement.getBoundingClientRect();
      this.dropdownTop = rect.bottom + 8;
      // Align right edge of dropdown with right edge of button
      this.dropdownLeft = Math.max(8, rect.right - 320);
    }
    this.open.set(isOpen);
    if (isOpen) {
      this.cargarNotificaciones();
    }
  }

  private cargarNotificaciones(): void {
    this.loading.set(true);
    this.notifService.listar({ size: 15 }).subscribe(page => {
      this.notificaciones.set(page.content);
      this.loading.set(false);
    });
  }

  marcarLeida(n: Notificacion): void {
    if (n.leida) return;
    this.notifService.marcarLeida(n.id).subscribe(() => {
      this.notificaciones.update(list =>
        list.map(item => item.id === n.id ? { ...item, leida: true } : item)
      );
      this.notifService.noLeidas.update(c => Math.max(0, c - 1));
    });
  }

  marcarTodas(): void {
    this.notifService.marcarTodasLeidas().subscribe(() => {
      this.notificaciones.update(list => list.map(item => ({ ...item, leida: true })));
      this.notifService.noLeidas.set(0);
    });
  }

  severidadIcon(s: SeveridadNotificacion) {
    switch (s) {
      case 'URGENTE': return AlertOctagon;
      case 'IMPORTANTE': return AlertTriangle;
      default: return Info;
    }
  }

  severidadColor(s: SeveridadNotificacion): string {
    switch (s) {
      case 'URGENTE': return 'text-red-500';
      case 'IMPORTANTE': return 'text-amber-500';
      default: return 'text-blue-400';
    }
  }

  tiempoRelativo(dateStr: string): string {
    const diff = Date.now() - new Date(dateStr).getTime();
    if (isNaN(diff)) return '';
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return 'ahora';
    if (mins < 60) return `hace ${mins} min`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `hace ${hours}h`;
    const days = Math.floor(hours / 24);
    if (days === 1) return 'ayer';
    if (days < 7) return `hace ${days} días`;
    return new Date(dateStr).toLocaleDateString('es-PY', { day: 'numeric', month: 'short' });
  }
}

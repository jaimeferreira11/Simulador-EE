import { Component, inject, computed, effect, OnInit, OnDestroy } from '@angular/core';
import { Router, RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { LucideAngularModule, LayoutDashboard, CirclePlus, Users, Zap, BarChart3, LogOut, Calendar, Trophy, FileText, Download, Gauge, ClipboardList, Menu, X, LucideIconData } from 'lucide-angular';
import { Subscription } from 'rxjs';
import { AuthStore } from '../../core/stores/auth.store';
import { CompetenciaStore } from '../../core/stores/competencia.store';
import { BreadcrumbComponent } from '../../shared/components/breadcrumb/breadcrumb.component';
import { NotificacionesComponent } from '../../shared/components/notificaciones/notificaciones.component';
import { DemoBannerComponent } from '../../shared/components/demo-banner/demo-banner.component';
import { DemoCeoToggleComponent } from '../../shared/components/demo-ceo-toggle/demo-ceo-toggle.component';
import { WebSocketService } from '../../core/websocket/websocket.service';
import { NotificacionApiService } from '../../core/services/notificacion-api.service';
import { DemoService } from '../../core/services/demo.service';
import { HotToastService } from '@ngxpert/hot-toast';

interface NavItem {
  label: string;
  route: string;
  icon: LucideIconData;
  queryParams?: Record<string, unknown>;
  disabled?: boolean;
}

@Component({
  selector: 'app-moderador-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LucideAngularModule, BreadcrumbComponent, NotificacionesComponent, DemoBannerComponent, DemoCeoToggleComponent],
  templateUrl: './moderador-layout.component.html',
})
export class ModeradorLayoutComponent implements OnInit, OnDestroy {
  authStore = inject(AuthStore);
  competenciaStore = inject(CompetenciaStore);
  private router = inject(Router);
  private wsService = inject(WebSocketService);
  private notifService = inject(NotificacionApiService);
  private demo = inject(DemoService);
  private toast = inject(HotToastService);
  private wsSub?: Subscription;

  readonly icons = { LayoutDashboard, CirclePlus, Users, Zap, BarChart3, LogOut, Calendar, Trophy, FileText, Download, Gauge, ClipboardList, Menu, X };
  mobileMenuOpen = false;

  // Re-fetch notification count on WS reconnect (catches notifications created while offline)
  private wsReconnectEffect = effect(() => {
    if (this.wsService.connected()) {
      this.notifService.refrescarCount();
    }
  });

  // Mantiene la conexión WS viva en toda la sesión, atada a la competencia activa.
  private wsConnectEffect = effect(
    () => {
      const codigo = this.competenciaStore.competenciaActiva()?.codigo ?? null;
      if (codigo) this.wsService.connect(codigo);
      else this.wsService.disconnect();
    },
    { allowSignalWrites: true },
  );

  // ── Section 1: General (not tied to a competencia) ──
  generalItems: NavItem[] = [
    { label: 'Mis Competencias', route: '/moderador/dashboard', icon: this.icons.LayoutDashboard },
    { label: 'Crear Competencia', route: '/moderador/competencias/nueva', icon: this.icons.CirclePlus },
    { label: 'Usuarios', route: '/moderador/usuarios', icon: this.icons.Users },
  ];

  // ── Section 2: Competencia-specific items ──
  competenciaItems = computed<NavItem[]>(() => {
    const c = this.competenciaStore.competenciaActiva();
    const disabled = !c;
    const qp = c ? { competencia: c.id } : {};
    return [
      { label: 'Dashboard', route: '/moderador/competencia', icon: this.icons.Gauge, queryParams: qp, disabled },
      { label: 'Gestión de Equipos', route: '/moderador/equipos', icon: this.icons.Users, queryParams: qp, disabled },
      { label: 'Trimestres', route: '/moderador/trimestres', icon: this.icons.Calendar, queryParams: qp, disabled },
      { label: 'Decisiones', route: '/moderador/decisiones-equipos', icon: this.icons.ClipboardList, queryParams: qp, disabled },
      { label: 'Eventos', route: '/moderador/eventos', icon: this.icons.Zap, queryParams: qp, disabled },
      { label: 'Rankings', route: '/moderador/rankings', icon: this.icons.Trophy, queryParams: qp, disabled },
      { label: 'Resultados', route: '/moderador/resultados', icon: this.icons.BarChart3, queryParams: qp, disabled },
      { label: 'Bitácora', route: '/moderador/bitacora', icon: this.icons.FileText, queryParams: qp, disabled },
      { label: 'Exportar', route: '/moderador/reportes', icon: this.icons.Download, queryParams: qp, disabled },
    ];
  });

  competenciaNombre = computed(() => this.competenciaStore.competenciaActiva()?.nombre ?? null);

  ngOnInit(): void {
    this.wsSub = this.wsService.messages$.subscribe(event => {
      if (event.tipo === 'notificacion.nueva') {
        this.notifService.handleWsNotificacion(event.payload);
      }
      if (event.tipo === 'competencia.reiniciada') {
        this.demo.setModoCeoActivo(false);
        void this.competenciaStore.reload();
        this.toast.info('La demo fue reiniciada', { duration: 3000, position: 'top-right' });
      }
    });
  }

  ngOnDestroy(): void {
    this.wsSub?.unsubscribe();
    this.wsService.disconnect();
  }

  get userInitials(): string {
    const name = this.authStore.user()?.nombre_completo ?? '';
    return name
      .split(' ')
      .filter(Boolean)
      .map(w => w[0])
      .slice(0, 2)
      .join('')
      .toUpperCase();
  }

  onCompetenciaItemClick(item: NavItem, event: MouseEvent): void {
    if (item.disabled) {
      event.preventDefault();
      this.router.navigate(['/moderador/dashboard']);
    }
  }

  logout(): void {
    this.authStore.logout();
  }
}

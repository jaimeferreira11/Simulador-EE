import { Component, inject, effect, OnInit, OnDestroy } from '@angular/core';
import { Router, RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import {
  LucideAngularModule,
  Home,
  FileText,
  BarChart3,
  Trophy,
  Newspaper,
  Users,
  MessageSquare,
  User,
  LogOut,
  Menu,
  X,
  BookOpen,
  Bot,
} from 'lucide-angular';
import { HotToastService } from '@ngxpert/hot-toast';
import { Subscription } from 'rxjs';
import { AuthStore } from '../../core/stores/auth.store';
import { BreadcrumbComponent } from '../../shared/components/breadcrumb/breadcrumb.component';
import { NotificacionesComponent } from '../../shared/components/notificaciones/notificaciones.component';
import { CompSwitcherComponent } from '../../shared/components/comp-switcher/comp-switcher.component';
import { OnboardingComponent } from '../../shared/components/onboarding/onboarding.component';
import { DemoBannerComponent } from '../../shared/components/demo-banner/demo-banner.component';
import { DemoCeoToggleComponent } from '../../shared/components/demo-ceo-toggle/demo-ceo-toggle.component';
import { OnboardingService } from '../../shared/services/onboarding.service';
import { WebSocketService } from '../../core/websocket/websocket.service';
import { NotificacionApiService } from '../../core/services/notificacion-api.service';
import { CompetenciaStore } from '../../core/stores/competencia.store';
import { DemoService } from '../../core/services/demo.service';

@Component({
  selector: 'app-jugador-layout',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    LucideAngularModule,
    BreadcrumbComponent,
    NotificacionesComponent,
    CompSwitcherComponent,
    OnboardingComponent,
    DemoBannerComponent,
    DemoCeoToggleComponent,
  ],
  templateUrl: './jugador-layout.component.html',
})
export class JugadorLayoutComponent implements OnInit, OnDestroy {
  authStore = inject(AuthStore);
  private router = inject(Router);
  private wsService = inject(WebSocketService);
  private notifService = inject(NotificacionApiService);
  private toast = inject(HotToastService);
  private onboarding = inject(OnboardingService);
  private competenciaStore = inject(CompetenciaStore);
  private demo = inject(DemoService);
  private wsSub?: Subscription;

  readonly icons = {
    Home,
    FileText,
    BarChart3,
    Trophy,
    Newspaper,
    Users,
    MessageSquare,
    User,
    LogOut,
    Menu,
    X,
    BookOpen,
    Bot,
  };
  mobileMenuOpen = false;

  /** Tutorial key per menu route — used in the template via data-tutorial attribute. */
  private readonly tutorialKeyByRoute: Record<string, string> = {
    '/jugador/competencia': 'mis-competencias',
    '/jugador/equipo': 'equipo',
    '/jugador/decisiones': 'decisiones',
    '/jugador/noticias': 'noticias',
    '/jugador/resultados': 'resultados',
    '/jugador/rankings': 'ranking',
    '/jugador/chat': 'chat',
  };

  tutorialKey(route: string): string | null {
    return this.tutorialKeyByRoute[route] ?? null;
  }

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

  menuItems = [
    { label: 'Inicio', route: '/jugador/competencia', icon: Home },
    { label: 'Decisiones', route: '/jugador/decisiones', icon: FileText },
    { label: 'Resultados', route: '/jugador/resultados', icon: BarChart3 },
    { label: 'Rankings', route: '/jugador/rankings', icon: Trophy },
    { label: 'Noticias', route: '/jugador/noticias', icon: Newspaper },
    { label: 'Mi Equipo', route: '/jugador/equipo', icon: Users },
    { label: 'Chat Equipo', route: '/jugador/chat', icon: MessageSquare },
  ];

  bottomItems = [
    { label: 'Ayuda', route: '/jugador/ayuda', icon: BookOpen },
    { label: 'Mi Perfil', route: '/jugador/perfil', icon: User },
  ];

  ngOnInit(): void {
    // First-time tutorial — only fires if the user has not completed it.
    // Delayed slightly to ensure the layout (and sidebar items) is rendered.
    setTimeout(() => this.onboarding.maybeStart(), 400);

    this.wsSub = this.wsService.messages$.subscribe((event) => {
      if (event.tipo === 'chat.mensaje' && !this.router.url.startsWith('/jugador/chat')) {
        const payload = event as any;
        const msg = payload.mensaje;
        if (msg) {
          this.toast.show(msg.nombre_usuario + ': ' + this.truncar(msg.contenido, 60), {
            duration: 4000,
            position: 'top-right',
            style: { background: '#1e3a5f', color: '#fff' },
          });
          this.notifService.noLeidas.update((c) => c + 1);
        }
      }

      // Handle WebSocket notification events
      if (event.tipo === 'notificacion.nueva') {
        this.notifService.handleWsNotificacion(event.payload);
      }

      // Demo reset: drop CEO mode, reload competencia, redirect to moderador area
      // if the user is a moderador acting as CEO.
      if (event.tipo === 'competencia.reiniciada') {
        const wasCeo = this.demo.modoCeoActivo();
        this.demo.setModoCeoActivo(false);
        void this.competenciaStore.reload();
        this.toast.info('La demo fue reiniciada', { duration: 3000, position: 'top-right' });
        if (wasCeo && this.authStore.rol() !== 'JUGADOR') {
          this.router.navigate(['/moderador/competencia']);
        }
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
      .map((w) => w[0])
      .slice(0, 2)
      .join('')
      .toUpperCase();
  }

  logout(): void {
    this.authStore.logout();
  }

  private truncar(text: string, max: number): string {
    return text.length > max ? text.substring(0, max - 3) + '...' : text;
  }
}

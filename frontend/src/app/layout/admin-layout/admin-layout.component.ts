import { Component, inject } from '@angular/core';
import { Router, RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import {
  LucideAngularModule, Users, Layers, TrendingUp, Settings, Zap, Building2, LogOut, Menu, X, Shield, LucideIconData,
} from 'lucide-angular';
import { AuthStore } from '../../core/stores/auth.store';
import { BreadcrumbComponent } from '../../shared/components/breadcrumb/breadcrumb.component';

interface NavItem {
  label: string;
  route: string;
  icon: LucideIconData;
}

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LucideAngularModule, BreadcrumbComponent],
  templateUrl: './admin-layout.component.html',
})
export class AdminLayoutComponent {
  authStore = inject(AuthStore);
  private router = inject(Router);

  readonly icons = { Users, Layers, TrendingUp, Settings, Zap, Building2, LogOut, Menu, X, Shield };
  mobileMenuOpen = false;

  navItems: NavItem[] = [
    { label: 'Usuarios', route: '/admin/usuarios', icon: this.icons.Users },
    { label: 'Rubros', route: '/admin/rubros', icon: this.icons.Layers },
    { label: 'Parámetros Macro', route: '/admin/parametros-macro', icon: this.icons.TrendingUp },
    { label: 'Parámetros Rubro', route: '/admin/parametros-rubro', icon: this.icons.Settings },
    { label: 'Eventos', route: '/admin/eventos', icon: this.icons.Zap },
    { label: 'Entidades', route: '/admin/entidades', icon: this.icons.Building2 },
  ];

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

  logout(): void {
    this.authStore.logout();
  }
}

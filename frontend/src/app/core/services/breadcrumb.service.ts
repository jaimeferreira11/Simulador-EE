import { Injectable, inject, signal } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';

export interface BreadcrumbItem {
  label: string;
  url: string;
}

const ROUTE_LABELS: Record<string, string> = {
  '/moderador/dashboard': 'Mis Competencias',
  '/moderador/competencia': 'Panel General',
  '/moderador/competencias/nueva': 'Nueva Competencia',
  '/moderador/equipos': 'Equipos',
  '/moderador/trimestres': 'Trimestres',
  '/moderador/decisiones-equipos': 'Decisiones',
  '/moderador/eventos': 'Eventos',
  '/moderador/rankings': 'Rankings',
  '/moderador/resultados': 'Resultados',
  '/moderador/bitacora': 'Bitácora',
  '/moderador/reportes': 'Exportar',
  '/jugador/competencia': 'Inicio',
  '/jugador/decisiones': 'Decisiones',
  '/jugador/decisiones-v2': 'Decisiones',
  '/jugador/resultados': 'Resultados',
  '/jugador/rankings': 'Rankings',
  '/jugador/equipo': 'Mi Equipo',
  '/jugador/perfil': 'Perfil',
};

// Pages that reset the trail (top-level entry points)
const ROOT_PAGES = new Set([
  '/moderador/dashboard',
  '/moderador/competencia',
  '/jugador/competencia',
]);

@Injectable({ providedIn: 'root' })
export class BreadcrumbService {
  private router = inject(Router);

  trail = signal<BreadcrumbItem[]>([]);

  constructor() {
    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd),
    ).subscribe(e => {
      const nav = e as NavigationEnd;
      this.onNavigate(nav.urlAfterRedirects);
    });
  }

  private onNavigate(fullUrl: string): void {
    const [path, queryString] = fullUrl.split('?');
    const urlWithQuery = fullUrl;

    // Build label for this page
    const label = this.labelFor(path);

    // If it's a root page, reset trail
    if (ROOT_PAGES.has(path)) {
      this.trail.set([{ label, url: urlWithQuery }]);
      return;
    }

    const current = this.trail();

    // Check if this page is already in the trail (user went back)
    const existingIdx = current.findIndex(item => {
      const [itemPath] = item.url.split('?');
      return itemPath === path;
    });

    if (existingIdx >= 0) {
      // Pop back to this point (update URL in case query params changed)
      const trimmed = current.slice(0, existingIdx + 1);
      trimmed[existingIdx] = { label, url: urlWithQuery };
      this.trail.set(trimmed);
    } else {
      // Push new page onto trail
      this.trail.set([...current, { label, url: urlWithQuery }]);
    }
  }

  private labelFor(path: string): string {
    // Exact match
    if (ROUTE_LABELS[path]) return ROUTE_LABELS[path];

    // Detail pages: /moderador/equipos/3 → "Detalle Equipo"
    const segments = path.split('/').filter(Boolean);
    if (segments.length >= 3) {
      const lastSeg = segments[segments.length - 1];
      const parentSeg = segments[segments.length - 2];
      if (/^\d+$/.test(lastSeg)) {
        switch (parentSeg) {
          case 'equipos': return 'Detalle Equipo';
          case 'trimestres': return 'Detalle Trimestre';
          case 'competencias': return 'Gestionar Competencia';
          case 'decisiones-equipos': return 'Detalle Decisiones';
          default: return 'Detalle';
        }
      }
    }

    // Fallback: last segment humanized
    const last = segments[segments.length - 1] ?? '';
    return last.charAt(0).toUpperCase() + last.slice(1).replace(/-/g, ' ');
  }

  /** Allow pages to override their label dynamically (e.g., show team name) */
  updateCurrentLabel(label: string): void {
    const current = this.trail();
    if (current.length === 0) return;
    const updated = [...current];
    updated[updated.length - 1] = { ...updated[updated.length - 1], label };
    this.trail.set(updated);
  }
}

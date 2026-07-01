import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of, EMPTY } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { HotToastService } from '@ngxpert/hot-toast';
import { Notificacion, NotificacionPage, SeveridadNotificacion } from '../models/notificacion.model';

@Injectable({ providedIn: 'root' })
export class NotificacionApiService {
  private http = inject(HttpClient);
  private toast = inject(HotToastService);
  private backendDisponible = false;
  private ultimaNotifId: number | null = null;
  private inicializado = false;

  noLeidas = signal(0);

  /** In-memory list of recently received notifications (newest first) */
  recientes = signal<Notificacion[]>([]);

  listar(params: { leida?: boolean; page?: number; size?: number } = {}): Observable<NotificacionPage> {
    if (!this.backendDisponible) {
      return of({ content: [], page: 0, size: 15, totalElements: 0, totalPages: 0 });
    }
    let httpParams = new HttpParams();
    if (params.leida !== undefined) httpParams = httpParams.set('leida', params.leida);
    if (params.page !== undefined) httpParams = httpParams.set('page', params.page);
    httpParams = httpParams.set('size', params.size ?? 15);

    return this.http.get<NotificacionPage>('/v1/notificaciones', { params: httpParams }).pipe(
      catchError(() => of({ content: [], page: 0, size: 15, totalElements: 0, totalPages: 0 })),
    );
  }

  private contarNoLeidas(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>('/v1/notificaciones/count', {
      params: new HttpParams().set('leida', false),
    });
  }

  marcarLeida(id: number): Observable<void> {
    if (!this.backendDisponible) return EMPTY;
    return this.http.post<void>(`/v1/notificaciones/${id}/leer`, {});
  }

  marcarTodasLeidas(): Observable<void> {
    if (!this.backendDisponible) return EMPTY;
    return this.http.post<void>('/v1/notificaciones/leer-todas', {});
  }

  /**
   * Handle a `notificacion.nueva` event received via WebSocket.
   * Increments unread counter, prepends to in-memory list, and shows a toast.
   */
  handleWsNotificacion(payload: Record<string, unknown>): void {
    const notif: Notificacion = {
      id: (payload['id'] as number) ?? Date.now(),
      tipo: (payload['tipo_notificacion'] as string) ?? 'GENERAL',
      titulo: (payload['titulo'] as string) ?? 'Nueva notificacion',
      descripcion: (payload['descripcion'] as string | null) ?? null,
      severidad: (payload['severidad'] as SeveridadNotificacion) ?? 'INFO',
      leida: false,
      created_at: (payload['created_at'] as string) ?? new Date().toISOString(),
      competencia_id: (payload['competencia_id'] as number | null) ?? null,
      datos_extra: (payload['datos_extra'] as Record<string, unknown> | null) ?? null,
    };

    // Update unread counter
    this.noLeidas.update(c => c + 1);

    // Prepend to in-memory list (keep at most 30)
    this.recientes.update(list => [notif, ...list].slice(0, 30));

    // Update ultimaNotifId
    if (notif.id && (!this.ultimaNotifId || notif.id > this.ultimaNotifId)) {
      this.ultimaNotifId = notif.id;
    }

    // Show toast
    this.mostrarToast(notif.titulo, notif.severidad);
  }

  /**
   * Re-fetch unread count from backend.
   * Call on WS reconnect to catch notifications created while the socket was offline.
   */
  refrescarCount(): void {
    if (!this.backendDisponible) return;
    this.contarNoLeidas().pipe(
      catchError(() => of({ count: 0 })),
    ).subscribe(res => this.noLeidas.set(res.count));
  }

  /**
   * One-shot initialization — probe backend, seed unread count and ultimaNotifId.
   * Replaces the legacy polling loop: real-time updates now arrive via WebSocket
   * (`notificacion.nueva` events handled by `handleWsNotificacion`).
   */
  inicializar(): void {
    if (this.inicializado) return;
    this.inicializado = true;
    this.contarNoLeidas().pipe(
      tap(res => {
        this.backendDisponible = true;
        this.noLeidas.set(res.count);
      }),
      catchError(() => {
        this.backendDisponible = false;
        return EMPTY;
      }),
    ).subscribe(() => {
      // Seed: fetch latest notification ID to avoid showing stale ones as toasts
      this.listar({ size: 1 }).subscribe(page => {
        if (page.content.length > 0) {
          this.ultimaNotifId = page.content[0].id;
        }
      });
    });
  }

  mostrarToast(titulo: string, severidad: SeveridadNotificacion): void {
    const label = severidad === 'URGENTE' ? 'URGENTE' : severidad === 'IMPORTANTE' ? 'IMPORTANTE' : 'INFO';
    const style = severidad === 'URGENTE'
      ? { background: '#7f1d1d', color: '#fff' }
      : severidad === 'IMPORTANTE'
        ? { background: '#78350f', color: '#fff' }
        : { background: '#1e3a5f', color: '#fff' };

    this.toast.show(`[${label}] ${titulo}`, {
      duration: 5000,
      position: 'top-right',
      style,
    });
  }
}

import {
  Component,
  inject,
  OnInit,
  OnDestroy,
  signal,
  computed,
  ElementRef,
  ViewChild,
  AfterViewChecked,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Send, ArrowDown } from 'lucide-angular';
import { JugadorStore } from '../../../core/stores/jugador.store';
import { AuthStore } from '../../../core/stores/auth.store';
import { WebSocketService } from '../../../core/websocket/websocket.service';
import { ChatApiService } from '../../../core/services/chat-api.service';
import { ChatMensaje } from '../../../core/models/chat.model';
import { Subscription } from 'rxjs';

interface ChatMensajeView extends ChatMensaje {
  agrupar_con_anterior: boolean;
  es_ultimo_de_grupo: boolean;
  es_propio: boolean;
}

const MAX_MENSAJE_LENGTH = 1000;
const AGRUPACION_VENTANA_MS = 5 * 60 * 1000; // 5 minutos

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [FormsModule, LucideAngularModule],
  templateUrl: './chat.component.html',
})
export class ChatComponent implements OnInit, OnDestroy, AfterViewChecked {
  readonly icons = { Send, ArrowDown };
  readonly maxLength = MAX_MENSAJE_LENGTH;

  private jugadorStore = inject(JugadorStore);
  private authStore = inject(AuthStore);
  private wsService = inject(WebSocketService);
  private chatApi = inject(ChatApiService);
  private wsSub?: Subscription;

  @ViewChild('messagesContainer') messagesContainer!: ElementRef<HTMLDivElement>;
  @ViewChild('inputTextarea') inputTextarea?: ElementRef<HTMLTextAreaElement>;

  mensajes = signal<ChatMensaje[]>([]);
  nuevoMensaje = '';
  loading = signal(false);
  loadingMore = signal(false);
  currentPage = 0;
  totalPages = 0;
  private isAtBottom = true;
  private pendingScrollToBottom = false;

  nuevosMensajesCount = signal(0);

  equipo = this.jugadorStore.equipo;
  competencia = this.jugadorStore.competencia;
  connected = this.wsService.connected;

  equipoNombre = computed(() => this.equipo()?.nombre_empresa ?? 'Equipo');
  equipoIniciales = computed(() => {
    const nombre = this.equipoNombre();
    return nombre.split(' ').filter(Boolean).map(p => p[0]).join('').substring(0, 2).toUpperCase();
  });
  miembrosCount = computed(() => this.equipo()?.miembros.length ?? 0);

  caracteresRestantes = computed(() => this.maxLength - (this.nuevoMensaje?.length ?? 0));
  mostrarContador = computed(() => this.caracteresRestantes() <= 100);
  excedeLimite = computed(() => this.caracteresRestantes() < 0);

  puedeEnviar = computed(() => {
    const tieneContenido = (this.nuevoMensaje?.trim().length ?? 0) > 0;
    return this.connected() && tieneContenido && !this.excedeLimite();
  });

  mensajesView = computed<ChatMensajeView[]>(() => {
    const list = this.mensajes();
    const userId = this.authStore.user()?.id;
    const result: ChatMensajeView[] = [];
    for (let i = 0; i < list.length; i++) {
      const m = list[i];
      const prev = i > 0 ? list[i - 1] : undefined;
      const sameAuthor = !!prev && prev.usuario_id === m.usuario_id;
      const dentroVentana =
        !!prev &&
        Math.abs(new Date(m.created_at).getTime() - new Date(prev.created_at).getTime()) <=
          AGRUPACION_VENTANA_MS;
      const agrupar = sameAuthor && dentroVentana;

      const next = i < list.length - 1 ? list[i + 1] : undefined;
      const sameAuthorNext = !!next && next.usuario_id === m.usuario_id;
      const dentroVentanaNext =
        !!next &&
        Math.abs(new Date(next.created_at).getTime() - new Date(m.created_at).getTime()) <=
          AGRUPACION_VENTANA_MS;
      const esUltimoDeGrupo = !(sameAuthorNext && dentroVentanaNext);

      result.push({
        ...m,
        agrupar_con_anterior: agrupar,
        es_ultimo_de_grupo: esUltimoDeGrupo,
        es_propio: m.usuario_id === userId,
      });
    }
    return result;
  });

  ngOnInit(): void {
    this.initData();
  }

  ngOnDestroy(): void {
    this.wsSub?.unsubscribe();
    const eq = this.equipo();
    if (eq) {
      this.wsService.markChatHidden(eq.id);
    }
  }

  ngAfterViewChecked(): void {
    if (this.pendingScrollToBottom) {
      this.pendingScrollToBottom = false;
      this.scrollToBottom();
    }
  }

  private async initData(): Promise<void> {
    await this.jugadorStore.init();

    const comp = this.competencia();
    const eq = this.equipo();
    if (!comp || !eq) return;

    // Mark chat as visible
    this.wsService.markChatVisible(eq.id);

    // Load initial messages via REST
    this.loading.set(true);
    this.chatApi.listar(eq.id, comp.id, 0, 30).subscribe(page => {
      // API returns newest first, reverse for display (oldest at top)
      this.mensajes.set(page.content.reverse());
      this.totalPages = page.total_pages;
      this.currentPage = 0;
      this.loading.set(false);
      this.pendingScrollToBottom = true;
    });

    // Listen for real-time messages
    this.wsSub = this.wsService.messages$.subscribe(event => {
      if (event.tipo === 'chat.mensaje') {
        const payload = event as any;
        if (payload.equipoId !== eq.id) return;

        const msg = payload.mensaje as ChatMensaje;
        // Avoid duplicates
        const exists = this.mensajes().some(m => m.id === msg.id);
        if (!exists) {
          const userId = this.authStore.user()?.id;
          const esPropio = msg.usuario_id === userId;
          this.mensajes.update(msgs => [...msgs, msg]);
          if (esPropio || this.isAtBottom) {
            this.scrollToBottom();
          } else {
            this.nuevosMensajesCount.update(n => n + 1);
          }
        }
      }
    });
  }

  onScroll(event: Event): void {
    const el = event.target as HTMLElement;
    this.isAtBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 50;

    if (this.isAtBottom && this.nuevosMensajesCount() > 0) {
      this.nuevosMensajesCount.set(0);
    }

    // Load more when scrolled to top
    if (el.scrollTop === 0 && this.currentPage + 1 < this.totalPages && !this.loadingMore()) {
      this.loadMore(el);
    }
  }

  private loadMore(el: HTMLElement): void {
    const comp = this.competencia();
    const eq = this.equipo();
    if (!comp || !eq) return;

    const prevHeight = el.scrollHeight;
    this.loadingMore.set(true);
    this.currentPage++;

    this.chatApi.listar(eq.id, comp.id, this.currentPage, 30).subscribe(page => {
      const older = page.content.reverse();
      this.mensajes.update(msgs => [...older, ...msgs]);
      this.loadingMore.set(false);

      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          el.scrollTop = el.scrollHeight - prevHeight;
        });
      });
    });
  }

  enviarMensaje(): void {
    if (!this.puedeEnviar()) return;
    const contenido = this.nuevoMensaje.trim();
    if (!contenido) return;

    const eq = this.equipo();
    if (!eq) return;

    // Send via WebSocket (server persists and broadcasts back)
    this.wsService.sendChat(eq.id, contenido);
    this.nuevoMensaje = '';
    this.resetTextareaHeight();
  }

  onInputKeydown(event: KeyboardEvent): void {
    // Ignore Enter while IME composition is active
    if (event.isComposing || (event as any).keyCode === 229) return;
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.enviarMensaje();
    }
  }

  onInputChange(): void {
    this.autoresizeTextarea();
  }

  private autoresizeTextarea(): void {
    const el = this.inputTextarea?.nativeElement;
    if (!el) return;
    el.style.height = 'auto';
    const max = 160; // px ~ approx 6 lines
    const next = Math.min(el.scrollHeight, max);
    el.style.height = next + 'px';
  }

  private resetTextareaHeight(): void {
    const el = this.inputTextarea?.nativeElement;
    if (!el) return;
    el.style.height = 'auto';
  }

  irAlFinal(): void {
    this.nuevosMensajesCount.set(0);
    this.scrollToBottom();
  }

  iniciales(nombre: string): string {
    return nombre.split(' ').filter(Boolean).map(p => p[0]).join('').substring(0, 2).toUpperCase();
  }

  formatHora(dateStr: string): string {
    return new Date(dateStr).toLocaleTimeString('es-PY', { hour: '2-digit', minute: '2-digit' });
  }

  private scrollToBottom(): void {
    // Double rAF: ensures Angular has committed signal-driven DOM updates
    // and the browser has performed layout before we read scrollHeight.
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        const el = this.messagesContainer?.nativeElement;
        if (el) {
          el.scrollTop = el.scrollHeight;
          this.isAtBottom = true;
          this.nuevosMensajesCount.set(0);
        }
      });
    });
  }
}

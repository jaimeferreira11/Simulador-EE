import { Injectable, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { AuthStore } from '../stores/auth.store';

export interface GameEvent {
  tipo: string;
  timestamp: string;
  competencia_id: number;
  payload: Record<string, unknown>;
}

@Injectable({ providedIn: 'root' })
export class WebSocketService {
  private authStore = inject(AuthStore);
  private socket: WebSocket | null = null;
  private messagesSubject = new Subject<GameEvent>();
  private reconnectAttempts = 0;
  private maxReconnectDelay = 30000;
  private currentCodigo: string | null = null;

  connected = signal(false);
  messages$ = this.messagesSubject.asObservable();
  lastEvent = signal<GameEvent | null>(null);

  connect(codigo: string): void {
    // Idempotente: si ya hay socket abierto (o conectando) al mismo código, no reabrir.
    if (
      this.currentCodigo === codigo &&
      this.socket &&
      (this.socket.readyState === WebSocket.OPEN ||
        this.socket.readyState === WebSocket.CONNECTING)
    ) {
      return;
    }
    this.disconnect();
    this.currentCodigo = codigo;
    const token = this.authStore.accessToken();
    if (!token) return;

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    const url = `${protocol}//${host}/ws/competencias/${codigo}?token=${token}`;

    this.socket = new WebSocket(url);

    this.socket.onopen = () => {
      this.connected.set(true);
      this.reconnectAttempts = 0;
    };

    this.socket.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data) as GameEvent;
        this.lastEvent.set(data);
        this.messagesSubject.next(data);
      } catch {
        // Ignore non-JSON messages (e.g., pong)
      }
    };

    this.socket.onclose = () => {
      this.connected.set(false);
      this.scheduleReconnect();
    };

    this.socket.onerror = () => {
      this.socket?.close();
    };
  }

  send(data: Record<string, unknown>): void {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(data));
    }
  }

  sendChat(equipoId: number, contenido: string): void {
    this.send({ tipo: 'chat.enviar', equipoId, contenido });
  }

  markChatVisible(equipoId: number): void {
    this.send({ tipo: 'chat.visto', equipoId });
  }

  markChatHidden(equipoId: number): void {
    this.send({ tipo: 'chat.salir', equipoId });
  }

  disconnect(): void {
    this.currentCodigo = null;
    this.reconnectAttempts = 0;
    if (this.socket) {
      this.socket.onclose = null;
      this.socket.close();
      this.socket = null;
    }
    this.connected.set(false);
  }

  private scheduleReconnect(): void {
    if (!this.currentCodigo) return;

    const delay = Math.min(
      1000 * Math.pow(2, this.reconnectAttempts),
      this.maxReconnectDelay
    );
    this.reconnectAttempts++;

    setTimeout(() => {
      if (this.currentCodigo) {
        this.connect(this.currentCodigo);
      }
    }, delay);
  }
}

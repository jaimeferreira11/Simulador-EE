import { TestBed } from '@angular/core/testing';
import { WebSocketService } from './websocket.service';
import { AuthStore } from '../stores/auth.store';

class FakeSocket {
  static OPEN = 1;
  static CONNECTING = 0;
  static CLOSED = 3;
  readyState = FakeSocket.CONNECTING;
  onopen: ((e?: unknown) => void) | null = null;
  onmessage: ((e?: unknown) => void) | null = null;
  onclose: ((e?: unknown) => void) | null = null;
  onerror: ((e?: unknown) => void) | null = null;
  constructor(public url: string) {
    creados.push(this);
  }
  close() {
    this.readyState = FakeSocket.CLOSED;
  }
}

let creados: FakeSocket[] = [];

describe('WebSocketService — connect idempotente', () => {
  let service: WebSocketService;
  let originalWebSocket: unknown;

  beforeEach(() => {
    creados = [];
    originalWebSocket = (window as unknown as { WebSocket: unknown }).WebSocket;
    (window as unknown as { WebSocket: unknown }).WebSocket = FakeSocket;
    TestBed.configureTestingModule({
      providers: [WebSocketService, { provide: AuthStore, useValue: { accessToken: () => 'tok' } }],
    });
    service = TestBed.inject(WebSocketService);
  });

  afterEach(() => {
    (window as unknown as { WebSocket: unknown }).WebSocket = originalWebSocket;
  });

  it('conectar dos veces al mismo código no reabre el socket', () => {
    service.connect('ABC');
    expect(creados.length).toBe(1);
    creados[0].readyState = FakeSocket.OPEN;
    service.connect('ABC');
    expect(creados.length).toBe(1);
  });

  it('conectar a un código distinto reconecta', () => {
    service.connect('ABC');
    creados[0].readyState = FakeSocket.OPEN;
    service.connect('XYZ');
    expect(creados.length).toBe(2);
    expect(creados[1].url).toContain('/ws/competencias/XYZ');
  });
});

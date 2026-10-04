import { TestBed } from '@angular/core/testing';
import { API_BASE_URL } from './api-config';
import { AddressWebSocketService } from './address-websocket.service';

describe('AddressWebSocketService', () => {
  let service: AddressWebSocketService;
  let socketInstances: MockWebSocket[];

  class MockWebSocket {
    static OPEN = 1;
    readyState = MockWebSocket.OPEN;
    url = '';
    onmessage: ((event: { data: string }) => void) | null = null;
    onclose: (() => void) | null = null;
    onopen: (() => void) | null = null;
    sent: string[] = [];

    constructor(url: string) {
      this.url = url;
      socketInstances.push(this);
    }

    addEventListener(type: string, listener: (event: { data: string }) => void): void {
      if (type === 'open') this.onopen = () => listener({ data: '' });
      if (type === 'close') this.onclose = () => listener({ data: '' });
      if (type === 'message') {
        this.onmessage = listener;
      }
    }

    send(data: string): void {
      this.sent.push(data);
    }

    close(): void {
      this.readyState = 0;
    }
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    vi.useFakeTimers();
    socketInstances = [];
    vi.stubGlobal('WebSocket', MockWebSocket);

    TestBed.configureTestingModule({
      providers: [{ provide: API_BASE_URL, useValue: 'http://localhost:8080/api' }],
    });
    service = TestBed.inject(AddressWebSocketService);
  });

  afterEach(() => {
    service.disconnect();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('conecta no canal do endereço', () => {
    service.connect('bc1qtest', vi.fn());
    expect(socketInstances[0]?.url).toBe('ws://localhost:8080/api/ws/address/bc1qtest');
  });

  it('resolve a base relativa de produção sem duplicar /api', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [{ provide: API_BASE_URL, useValue: '/api' }] });
    service = TestBed.inject(AddressWebSocketService);
    service.connect('bc1qtest', vi.fn());
    const expected = new URL('/api/ws/address/bc1qtest', window.location.href);
    expected.protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    expect(socketInstances[0]?.url).toBe(expected.toString());
  });

  it('notifica PET_ARTWORK_READY', () => {
    const handler = vi.fn();
    service.connect('bc1qtest', handler);
    const socket = socketInstances[0];
    socket.onmessage?.({
      data: JSON.stringify({
        type: 'EVENT',
        cursor: '1',
        data: {
          address: 'bc1qtest',
          eventType: 'PET_ARTWORK_READY',
          occurredAt: '2026-09-14T12:00:00Z',
          presentation: 'CREATURE',
          artworkVersion: 1,
          atlasUrl: '/api/v1/public/addresses/bc1qtest/artwork/1/atlas.png',
        },
      }),
    });

    expect(handler).toHaveBeenCalledOnce();
    expect(handler.mock.calls[0][0].data.artworkVersion).toBe(1);
  });

  it('responde PONG a PING', () => {
    service.connect('bc1qtest', vi.fn());
    const socket = socketInstances[0];
    socket.onmessage?.({ data: JSON.stringify({ type: 'PING' }) });
    expect(socket.sent).toContain(JSON.stringify({ type: 'PONG' }));
  });
  it('snapshot e eventos de saldo/estado invalidam dados mesmo sem arte nova', () => {
    const received: unknown[] = [];
    service.connect('bc1qtest', (event) => received.push(event));
    const socket = socketInstances[0];
    for (const [type, cursor, data] of [
      ['SNAPSHOT', '0', { address: 'bc1qtest', presentation: 'EGG' }],
      ['EVENT', '1', { address: 'bc1qtest', eventType: 'PET_STATE_CHANGED', state: 'FAMINTO' }],
      ['EVENT', '2', { address: 'bc1qtest', eventType: 'BITCOIN_BALANCE_RECONCILED' }],
    ])
      socket.onmessage?.({ data: JSON.stringify({ type, cursor, data }) });
    expect(received).toHaveLength(3);
  });

  it('reconecta com cursor exato sem repetir evento já aplicado', () => {
    const handler = vi.fn();
    service.connect('bc1qtest', handler);
    const socket = socketInstances[0];
    const event = {
      type: 'EVENT',
      cursor: '9007199254740993',
      data: { address: 'bc1qtest', eventType: 'PET_ARTWORK_READY' },
    };
    socket.onmessage?.({ data: JSON.stringify(event) });
    socket.onmessage?.({ data: JSON.stringify(event) });
    expect(handler).toHaveBeenCalledOnce();
    socket.onclose?.();
    vi.advanceTimersByTime(1000);
    expect(socketInstances).toHaveLength(2);
    socketInstances[1].onopen?.();
    expect(socketInstances[1].sent).toContain(
      JSON.stringify({ type: 'RECONNECT', cursor: '9007199254740993' }),
    );
  });

  it('ignora mensagens e close de um socket substituído do mesmo endereço', () => {
    const handler = vi.fn();
    service.connect('bc1qtest', handler);
    const previous = socketInstances[0];
    service.disconnect();
    service.connect('bc1qtest', handler);
    previous.onmessage?.({
      data: JSON.stringify({
        type: 'EVENT',
        cursor: '1',
        data: { eventType: 'PET_ARTWORK_READY' },
      }),
    });
    previous.onclose?.();
    vi.advanceTimersByTime(30_000);
    expect(handler).not.toHaveBeenCalled();
    expect(socketInstances).toHaveLength(2);
  });

  it('disconnect cancela a tentativa de reconexão pendente', () => {
    service.connect('bc1qtest', vi.fn());
    socketInstances[0].onclose?.();
    service.disconnect();
    vi.advanceTimersByTime(30_000);
    expect(socketInstances).toHaveLength(1);
  });
});

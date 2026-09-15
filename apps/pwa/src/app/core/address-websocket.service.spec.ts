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
    sent: string[] = [];

    constructor(url: string) {
      this.url = url;
      socketInstances.push(this);
    }

    addEventListener(type: string, listener: (event: { data: string }) => void): void {
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
    socketInstances = [];
    vi.stubGlobal('WebSocket', MockWebSocket);

    TestBed.configureTestingModule({
      providers: [{ provide: API_BASE_URL, useValue: 'http://localhost:8080' }],
    });
    service = TestBed.inject(AddressWebSocketService);
  });

  afterEach(() => {
    service.disconnect();
    vi.unstubAllGlobals();
  });

  it('conecta no canal do endereço', () => {
    service.connect('bc1qtest', vi.fn());
    expect(socketInstances[0]?.url).toBe('ws://localhost:8080/api/ws/address/bc1qtest');
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
    expect(handler.mock.calls[0][0].artworkVersion).toBe(1);
  });

  it('responde PONG a PING', () => {
    service.connect('bc1qtest', vi.fn());
    const socket = socketInstances[0];
    socket.onmessage?.({ data: JSON.stringify({ type: 'PING' }) });
    expect(socket.sent).toContain(JSON.stringify({ type: 'PONG' }));
  });
});

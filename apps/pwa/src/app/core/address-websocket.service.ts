import { Injectable, OnDestroy, inject } from '@angular/core';
import { API_BASE_URL } from './api-config';

export interface AddressUpdate {
  type: 'SNAPSHOT' | 'EVENT';
  cursor: string;
  data: Record<string, unknown>;
}

/** Canal público com snapshot, cursor exato e reconexão com espera limitada. */
@Injectable({ providedIn: 'root' })
export class AddressWebSocketService implements OnDestroy {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private socket: WebSocket | null = null;
  private currentAddress: string | null = null;
  private updateHandler: ((event: AddressUpdate) => void) | null = null;
  private cursor: string | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectDelay = 1000;

  connect(address: string, onUpdate: (event: AddressUpdate) => void): void {
    if (this.currentAddress === address && this.socket) {
      this.updateHandler = onUpdate;
      return;
    }
    this.disconnect();
    this.currentAddress = address;
    this.updateHandler = onUpdate;
    this.openSocket();
  }

  disconnect(): void {
    if (this.reconnectTimer !== null) clearTimeout(this.reconnectTimer);
    this.reconnectTimer = null;
    const previous = this.socket;
    this.socket = null;
    this.currentAddress = null;
    this.updateHandler = null;
    this.cursor = null;
    this.reconnectDelay = 1000;
    previous?.close();
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  private openSocket(): void {
    const address = this.currentAddress;
    if (!address) return;
    const socket = new WebSocket(this.buildWsUrl(address));
    this.socket = socket;
    let initialSnapshot = true;
    socket.addEventListener('open', () => {
      if (this.socket !== socket) return;
      if (this.cursor !== null)
        socket.send(JSON.stringify({ type: 'RECONNECT', cursor: this.cursor }));
    });
    socket.addEventListener('message', (event) => {
      if (this.socket !== socket) return;
      if (this.handleMessage(event.data, initialSnapshot)) initialSnapshot = false;
    });
    socket.addEventListener('close', () => {
      if (this.socket !== socket) return;
      this.socket = null;
      this.reconnectTimer = setTimeout(() => {
        this.reconnectTimer = null;
        this.openSocket();
      }, this.reconnectDelay);
      this.reconnectDelay = Math.min(this.reconnectDelay * 2, 30_000);
    });
  }

  private buildWsUrl(address: string): string {
    const url = new URL(
      `${this.apiBaseUrl.replace(/\/$/, '')}/ws/address/${encodeURIComponent(address)}`,
      window.location.href,
    );
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    return url.toString();
  }

  private handleMessage(raw: unknown, initialSnapshot: boolean): boolean {
    if (typeof raw !== 'string') return false;
    let envelope: Record<string, unknown>;
    try {
      const parsed: unknown = JSON.parse(raw);
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return false;
      envelope = parsed as Record<string, unknown>;
    } catch {
      return false;
    }
    if (envelope['type'] === 'PING') {
      this.socket?.send(JSON.stringify({ type: 'PONG' }));
      return false;
    }
    const type = envelope['type'];
    const cursor = envelope['cursor'];
    const data = envelope['data'];
    if (
      (type !== 'SNAPSHOT' && type !== 'EVENT') ||
      typeof cursor !== 'string' ||
      !/^\d{1,19}$/.test(cursor) ||
      !data ||
      typeof data !== 'object' ||
      Array.isArray(data)
    )
      return false;
    const payload = data as Record<string, unknown>;
    if (payload['address'] && payload['address'] !== this.currentAddress) return false;
    if (
      type === 'EVENT' &&
      (typeof payload['eventType'] !== 'string' || !/^(PET_|BITCOIN_)/.test(payload['eventType']))
    )
      return false;
    // O primeiro snapshot também permite reinício do servidor, cujo cursor em memória reinicia.
    if (
      !(type === 'SNAPSHOT' && initialSnapshot) &&
      this.cursor !== null &&
      BigInt(cursor) <= BigInt(this.cursor)
    )
      return false;
    this.cursor = cursor;
    this.reconnectDelay = 1000;
    this.updateHandler?.({ type, cursor, data: payload });
    return type === 'SNAPSHOT';
  }
}

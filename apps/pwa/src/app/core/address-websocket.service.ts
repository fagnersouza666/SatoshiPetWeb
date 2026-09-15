import { Injectable, inject } from '@angular/core';
import { API_BASE_URL } from './api-config';
import { PetArtworkReadyEvent } from './models/pet-art.model';

type ArtworkReadyHandler = (event: PetArtworkReadyEvent) => void;

interface WebSocketEnvelope {
  type: 'SNAPSHOT' | 'EVENT' | 'PING' | 'PONG';
  cursor?: string;
  data?: unknown;
}

/**
 * Cliente mínimo do canal WebSocket por endereço (ART-06/10).
 * Reage a PET_ARTWORK_READY recarregando assets versionados.
 */
@Injectable({ providedIn: 'root' })
export class AddressWebSocketService {
  private readonly apiBaseUrl = inject(API_BASE_URL);

  private socket: WebSocket | null = null;
  private currentAddress: string | null = null;
  private artworkReadyHandler: ArtworkReadyHandler | null = null;

  connect(address: string, onArtworkReady: ArtworkReadyHandler): void {
    if (this.currentAddress === address && this.socket?.readyState === WebSocket.OPEN) {
      this.artworkReadyHandler = onArtworkReady;
      return;
    }
    this.disconnect();
    this.currentAddress = address;
    this.artworkReadyHandler = onArtworkReady;

    const wsUrl = this.buildWsUrl(address);
    this.socket = new WebSocket(wsUrl);
    this.socket.addEventListener('message', (event) => this.handleMessage(event.data));
    this.socket.addEventListener('close', () => {
      if (this.currentAddress === address) {
        this.socket = null;
      }
    });
  }

  disconnect(): void {
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
    this.currentAddress = null;
    this.artworkReadyHandler = null;
  }

  private buildWsUrl(address: string): string {
    const httpBase = this.apiBaseUrl.replace(/\/$/, '');
    const wsBase = httpBase.replace(/^http/i, 'ws');
    return `${wsBase}/api/ws/address/${encodeURIComponent(address)}`;
  }

  private handleMessage(raw: unknown): void {
    if (typeof raw !== 'string') {
      return;
    }
    let envelope: WebSocketEnvelope;
    try {
      envelope = JSON.parse(raw) as WebSocketEnvelope;
    } catch {
      return;
    }

    if (envelope.type === 'PING') {
      this.socket?.send(JSON.stringify({ type: 'PONG' }));
      return;
    }

    if (envelope.type !== 'EVENT' || !envelope.data || typeof envelope.data !== 'object') {
      return;
    }

    const payload = envelope.data as Record<string, unknown>;
    if (payload['eventType'] !== 'PET_ARTWORK_READY') {
      return;
    }

    const ready: PetArtworkReadyEvent = {
      address: String(payload['address'] ?? ''),
      eventType: 'PET_ARTWORK_READY',
      occurredAt: String(payload['occurredAt'] ?? ''),
      presentation: payload['presentation'] === 'EGG' ? 'EGG' : 'CREATURE',
      artworkVersion: Number(payload['artworkVersion'] ?? 0),
      atlasUrl: String(payload['atlasUrl'] ?? ''),
    };
    this.artworkReadyHandler?.(ready);
  }
}

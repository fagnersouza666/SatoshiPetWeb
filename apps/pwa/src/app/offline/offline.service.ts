import { Injectable, OnDestroy, signal } from '@angular/core';

/**
 * Serviço de estado offline.
 *
 * Detecta mudanças em `navigator.onLine` via eventos da Window e expõe
 * o estado atual como signal reativo. Leitura offline desabilita alterações
 * financeiras conforme CA-031 e requisito de offline da PWA.
 */
@Injectable({ providedIn: 'root' })
export class OfflineService implements OnDestroy {
  /** `true` quando o dispositivo está sem conexão. */
  readonly isOffline = signal<boolean>(typeof navigator !== 'undefined' ? !navigator.onLine : false);

  private readonly handleOnline = (): void => this.isOffline.set(false);
  private readonly handleOffline = (): void => this.isOffline.set(true);

  constructor() {
    if (typeof window !== 'undefined') {
      window.addEventListener('online', this.handleOnline);
      window.addEventListener('offline', this.handleOffline);
    }
  }

  ngOnDestroy(): void {
    if (typeof window !== 'undefined') {
      window.removeEventListener('online', this.handleOnline);
      window.removeEventListener('offline', this.handleOffline);
    }
  }
}

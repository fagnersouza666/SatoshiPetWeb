import { Injectable } from '@angular/core';

/**
 * Serviço de limpeza de caches privados (CA-067).
 *
 * Remove do Cache API do navegador os buckets que podem conter
 * dados privados da conta (histórico de transações, saldo, estado do pet).
 * Invocado obrigatoriamente no logout antes de limpar a sessão em memória.
 */
@Injectable({ providedIn: 'root' })
export class PrivateCacheService {
  /**
   * Prefixos dos caminhos de API que podem conter dados privados.
   *
   * O prefixo `/api/v1/conta` é mantido para instalações que ainda tenham
   * respostas da versão anterior do contrato. O caminho vigente é
   * `/api/v1/account`.
   */
  private readonly PRIVATE_API_PATHS = ['/api/v1/account', '/api/v1/auth', '/api/v1/conta'];

  /**
   * Marcadores de buckets criados especificamente para dados privados.
   *
   * O Angular Service Worker normalmente guarda os data groups em um bucket
   * compartilhado, por isso a limpeza também inspeciona as entradas abaixo.
   */
  private readonly PRIVATE_CACHE_NAME_MARKERS = [
    'private',
    'ngsw:db:/api/v1/account',
    'ngsw:db:/api/v1/auth',
    'ngsw:db:/api/v1/conta',
  ];

  /**
   * Limpa todos os caches do browser que contêm dados privados da conta.
   *
   * Silencia erros individuais para não interromper o fluxo de logout;
   * a sessão em memória é limpa de qualquer forma pelo SessionService.
   */
  async clearPrivateCaches(): Promise<void> {
    if (typeof caches === 'undefined') {
      return;
    }

    let cacheNames: string[];
    try {
      cacheNames = await caches.keys();
    } catch {
      // Cache API indisponível neste contexto
      return;
    }

    await Promise.allSettled(cacheNames.map((name) => this.clearCache(name)));
  }

  /** Limpa um bucket privado inteiro ou somente suas respostas privadas. */
  private async clearCache(name: string): Promise<void> {
    if (this.isPrivateCacheName(name)) {
      await caches.delete(name);
      return;
    }

    const cache = await caches.open(name);
    const requests = await cache.keys();
    const privateRequests = requests.filter((request) => this.isPrivateRequest(request));

    await Promise.allSettled(privateRequests.map((request) => cache.delete(request)));
  }

  private isPrivateCacheName(name: string): boolean {
    return this.PRIVATE_CACHE_NAME_MARKERS.some((marker) => name.includes(marker));
  }

  private isPrivateRequest(request: Request): boolean {
    let pathname: string;
    try {
      pathname = new URL(request.url).pathname;
    } catch {
      return false;
    }

    if (pathname.startsWith('/api/v1/public/')) {
      return false;
    }

    return this.PRIVATE_API_PATHS.some(
      (path) => pathname === path || pathname.startsWith(`${path}/`),
    );
  }
}

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
   * Substrings identificadoras de caches que contêm dados privados.
   * Prefixos ngsw e buckets de API de conta.
   */
  private readonly PRIVATE_CACHE_KEYS = [
    '/api/v1/conta',
    '/api/v1/auth',
    'ngsw:db:/api/v1/conta',
    'private',
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

    const privateCacheNames = cacheNames.filter((name) =>
      this.PRIVATE_CACHE_KEYS.some((key) => name.includes(key)),
    );

    await Promise.allSettled(privateCacheNames.map((name) => caches.delete(name)));
  }
}

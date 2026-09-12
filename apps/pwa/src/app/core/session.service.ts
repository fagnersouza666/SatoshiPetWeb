import { Injectable, computed, signal } from '@angular/core';
import { AccountInfo } from './models/account.model';

/**
 * Serviço de estado da sessão autenticada.
 *
 * Expõe signals reativos para o estado de autenticação e dados da conta.
 * Usado pelo AuthGuard e pelos componentes de conta.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly _account = signal<AccountInfo | null>(null);

  /** Dados da conta autenticada, ou `null` se não autenticado. */
  readonly account = this._account.asReadonly();

  /** `true` quando há uma sessão autenticada ativa. */
  readonly isAuthenticated = computed(() => this._account() !== null);

  /**
   * Persiste os dados da conta na sessão.
   * Chamado após verificação bem-sucedida do magic-link.
   */
  setSession(account: AccountInfo): void {
    this._account.set(account);
  }

  /**
   * Limpa os dados da sessão.
   * Chamado no logout — complementar à limpeza de caches (CA-067).
   */
  clearSession(): void {
    this._account.set(null);
  }
}

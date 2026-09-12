import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { ApiClientService } from './api-client.service';
import { SessionService } from './session.service';
import { PrivateCacheService } from './private-cache.service';
import { AccountInfo, RegisterPayload, VerifyResponse } from './models/account.model';

/**
 * Serviço de autenticação por magic-link (épico CONTA).
 *
 * Fluxo:
 * 1. `requestMagicLink(email)` → servidor envia e-mail com link
 * 2. `verifyToken(token)` → valida token do link; retorna se é usuário novo
 * 3. Se novo usuário → `register(payload)` → cadastra endereço + nome do pet
 * 4. `logout()` → encerra sessão + limpa caches privados (CA-067)
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiClientService);
  private readonly session = inject(SessionService);
  private readonly privateCache = inject(PrivateCacheService);
  private readonly router = inject(Router);

  /**
   * Estado temporário entre a verificação do token e o cadastro.
   * Armazenado em memória — não persiste entre sessões (requisito de segurança).
   */
  private readonly _pendingVerify = signal<VerifyResponse | null>(null);

  /** Resultado da verificação aguardando conclusão do cadastro. */
  readonly pendingVerify = this._pendingVerify.asReadonly();

  /**
   * Solicita o envio de magic-link para o e-mail informado.
   * POST /v1/auth/magic-link
   */
  requestMagicLink(email: string): Observable<void> {
    return this.api.post<void>('/v1/auth/magic-link', { email });
  }

  /**
   * Verifica o token do magic-link.
   * POST /v1/auth/verify
   *
   * Em caso de sucesso:
   * - Usuário existente → popula SessionService e navega para /conta
   * - Novo usuário → armazena resultado pendente e navega para /cadastro
   */
  async verifyAndNavigate(token: string): Promise<VerifyResponse> {
    const result = await firstValueFrom(
      this.api.post<{ account?: AccountInfo; email: string; isNewUser: boolean }>(
        '/v1/auth/verify',
        { token },
      ),
    );

    if (!result.isNewUser && result.account) {
      this.session.setSession(result.account);
      await this.router.navigate(['/conta']);
    } else {
      this._pendingVerify.set({ email: result.email, isNewUser: result.isNewUser });
      await this.router.navigate(['/cadastro']);
    }

    return { email: result.email, isNewUser: result.isNewUser };
  }

  /**
   * Verifica token cru (sem navegação) — para uso nos testes e fluxos alternativos.
   * POST /v1/auth/verify
   */
  verifyToken(token: string): Observable<VerifyResponse> {
    return this.api.post<VerifyResponse>('/v1/auth/verify', { token });
  }

  /**
   * Completa o registro do novo usuário (cadastro de endereço + nome do pet).
   * POST /v1/auth/register
   */
  register(payload: RegisterPayload): Observable<AccountInfo> {
    return this.api.post<AccountInfo>('/v1/auth/register', payload);
  }

  /**
   * Encerra a sessão do usuário.
   *
   * Ordem obrigatória (CA-067):
   * 1. Notifica o servidor (invalida cookie de sessão)
   * 2. Limpa caches privados do browser
   * 3. Limpa sessão em memória
   * 4. Redireciona para /entrar
   */
  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.api.post<void>('/v1/auth/logout', {}));
    } catch {
      // Sessão pode já ter expirado no servidor; prosseguir com limpeza local
    }

    await this.privateCache.clearPrivateCaches();
    this._pendingVerify.set(null);
    this.session.clearSession();
    await this.router.navigate(['/entrar']);
  }
}

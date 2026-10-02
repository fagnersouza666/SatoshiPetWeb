import { Injectable, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, firstValueFrom, catchError, map, switchMap, tap, throwError } from 'rxjs';
import { ApiClientService } from './api-client.service';
import { SessionService } from './session.service';
import { PrivateCacheService } from './private-cache.service';
import { AccountInfo, RegisterPayload, VerifyResponse } from './models/account.model';

interface VerifyApiResponse {
  status: string;
  registrationRequired: boolean;
  verifiedEmail: string;
}

interface AccountApiResponse {
  id: string;
  email: string;
  bitcoinAddress?: string;
  petName?: string;
}

/** Autenticação por magic-link e recuperação conforme os DTOs da API. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiClientService);
  private readonly session = inject(SessionService);
  private readonly privateCache = inject(PrivateCacheService);
  private readonly router = inject(Router);
  private readonly _pendingVerify = signal<VerifyResponse | null>(null);
  // O magic-link é reutilizado no cadastro e nunca persiste fora desta instância.
  private registrationToken: string | null = null;
  // Após autenticação confirmada, uma falha transitória de /me só repete a leitura.
  private pendingSession: {
    flow: 'verify' | 'register' | 'recover';
    credential?: string;
    verified?: VerifyResponse;
  } | null = null;
  readonly pendingVerify = this._pendingVerify.asReadonly();

  requestMagicLink(email: string): Observable<void> {
    return this.api.post<void>('/v1/auth/magic-link', { email });
  }

  async verifyAndNavigate(token: string): Promise<VerifyResponse> {
    if (this.pendingSession?.flow === 'verify' && this.pendingSession.credential === token) {
      const verified = this.pendingSession.verified!;
      await this.openAccount();
      return verified;
    }
    this.pendingSession = null;
    this.clearPendingRegistration();
    const result = await firstValueFrom(this.verifyToken(token));
    if (result.isNewUser) {
      this.registrationToken = token;
      this._pendingVerify.set(result);
      await this.router.navigate(['/cadastro']);
    } else {
      this.pendingSession = { flow: 'verify', credential: token, verified: result };
      this.session.clearSession();
      await this.openAccount();
    }
    return result;
  }

  verifyToken(token: string): Observable<VerifyResponse> {
    return this.api
      .post<VerifyApiResponse>('/v1/auth/magic-link/verify', { token })
      .pipe(
        map((result) => ({ email: result.verifiedEmail, isNewUser: result.registrationRequired })),
      );
  }

  register(payload: RegisterPayload): Observable<AccountInfo> {
    if (this.pendingSession?.flow === 'register') return this.loadAccount();
    if (!this.registrationToken) {
      return throwError(() => new Error('Verifique um novo link de acesso antes de cadastrar.'));
    }
    return this.api
      .post<{ status: string; accountId: string }>('/v1/auth/register', {
        token: this.registrationToken,
        bitcoinAddress: payload.address,
        petName: payload.petName,
      })
      .pipe(
        tap(() => {
          this.registrationToken = null;
          this.pendingSession = { flow: 'register' };
          this.session.clearSession();
        }),
        switchMap(() => this.loadAccount()),
      );
  }

  async recoverAndNavigate(code: string): Promise<void> {
    if (this.pendingSession?.flow !== 'recover' || this.pendingSession.credential !== code) {
      await firstValueFrom(
        this.api.post<{ status: string }>('/v1/account/recovery/reset', { code }),
      );
      this.pendingSession = { flow: 'recover', credential: code };
      this.clearPendingRegistration();
      this.session.clearSession();
    }
    await this.openAccount();
  }

  private loadAccount(): Observable<AccountInfo> {
    return this.api.getFresh<AccountApiResponse>('/v1/account/me').pipe(
      map((account) => ({
        id: account.id,
        email: account.email,
        address: account.bitcoinAddress,
        petName: account.petName,
      })),
      tap(() => {
        this.pendingSession = null;
        this.clearPendingRegistration();
      }),
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 401) {
          this.pendingSession = null;
          this.clearPendingRegistration();
          this.api.clearCsrfToken();
          this.session.clearSession();
        }
        return throwError(() => error);
      }),
    );
  }

  private async openAccount(): Promise<void> {
    const account = await firstValueFrom(this.loadAccount());
    this.session.setSession(account);
    await this.router.navigate(['/conta']);
  }

  private clearPendingRegistration(): void {
    this.registrationToken = null;
    this._pendingVerify.set(null);
  }

  /** Só declara saída depois de revogar a sessão ou confirmar que já expirou. */
  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.api.post<void>('/v1/auth/logout', {}));
    } catch (error) {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) throw error;
    }
    await this.privateCache.clearPrivateCaches();
    this.api.clearCsrfToken();
    this.pendingSession = null;
    this.clearPendingRegistration();
    this.session.clearSession();
    await this.router.navigate(['/entrar']);
  }
}

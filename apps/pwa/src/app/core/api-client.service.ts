import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable, catchError, defer, map, tap, throwError } from 'rxjs';
import { SessionService } from './session.service';
import { PrivateCacheService } from './private-cache.service';
import { API_BASE_URL } from './api-config';

/** HTTP com cookie de sessão e o contrato CSRF emitido pela API. */
@Injectable({ providedIn: 'root' })
export class ApiClientService {
  private readonly session = inject(SessionService);
  private readonly privateCache = inject(PrivateCacheService);
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  // O segredo CSRF permanece apenas em memória; nunca é gravado em cache/storage.
  private csrfToken: string | null = null;

  clearCsrfToken(): void {
    this.csrfToken = null;
  }

  private request<T>(
    method: string,
    path: string,
    body?: unknown,
    params?: Record<string, string>,
    cache?: RequestCache,
  ): Observable<T> {
    const privateRequest =
      /^\/v1\/account(?:\/|$)/.test(path) && !path.startsWith('/v1/account/recovery/');
    return defer(() => {
      const revision = this.session.revision();
      let headers = new HttpHeaders({ 'Content-Type': 'application/json' });
      if (this.csrfToken) headers = headers.set('X-CSRF-Token', this.csrfToken);
      return this.http
        .request<T>(method, `${this.baseUrl}${path}`, {
          body,
          headers,
          withCredentials: true,
          params:
            method === 'GET' && privateRequest
              ? new HttpParams({ fromObject: { ...params, 'ngsw-bypass': 'true' } })
              : params
                ? new HttpParams({ fromObject: params })
                : undefined,
          observe: 'response',
          cache: privateRequest ? 'no-store' : cache,
        })
        .pipe(
          tap((response) => {
            if (privateRequest && revision !== this.session.revision()) {
              throw new Error('Resposta de uma sessão anterior descartada.');
            }
            const csrf = response.headers.get('X-CSRF-Token');
            if (csrf && revision === this.session.revision()) this.csrfToken = csrf;
          }),
          map((response) => response.body as T),
          catchError((error: unknown) => {
            if (privateRequest && revision !== this.session.revision()) {
              return throwError(() => new Error('Resposta de uma sessão anterior descartada.'));
            }
            if (
              privateRequest &&
              revision === this.session.revision() &&
              error instanceof HttpErrorResponse &&
              error.status === 401
            ) {
              this.clearCsrfToken();
              this.session.clearSession();
              void this.privateCache.clearPrivateCaches();
            }
            return throwError(() => error);
          }),
        );
    });
  }

  get<T>(path: string, params?: Record<string, string>): Observable<T> {
    return this.request<T>('GET', path, undefined, params);
  }

  /** Identidade autenticada nunca pode vir de fallback offline ou cache HTTP. */
  getFresh<T>(path: string): Observable<T> {
    return this.request<T>('GET', path, undefined, { 'ngsw-bypass': 'true' }, 'no-store');
  }

  post<T>(path: string, body: unknown): Observable<T> {
    return this.request<T>('POST', path, body);
  }

  put<T>(path: string, body: unknown): Observable<T> {
    return this.request<T>('PUT', path, body);
  }

  delete<T>(path: string): Observable<T> {
    return this.request<T>('DELETE', path);
  }
}

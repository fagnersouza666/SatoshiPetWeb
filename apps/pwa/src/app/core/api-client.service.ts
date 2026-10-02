import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';
import { API_BASE_URL } from './api-config';

/** HTTP com cookie de sessão e o contrato CSRF emitido pela API. */
@Injectable({ providedIn: 'root' })
export class ApiClientService {
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
    let headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    if (this.csrfToken) headers = headers.set('X-CSRF-Token', this.csrfToken);
    return this.http
      .request<T>(method, `${this.baseUrl}${path}`, {
        body,
        headers,
        withCredentials: true,
        params: params ? new HttpParams({ fromObject: params }) : undefined,
        observe: 'response',
        cache,
      })
      .pipe(
        tap((response) => {
          const csrf = response.headers.get('X-CSRF-Token');
          if (csrf) this.csrfToken = csrf;
        }),
        map((response) => response.body as T),
      );
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

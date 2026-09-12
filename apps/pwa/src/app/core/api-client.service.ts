import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-config';

/**
 * Wrapper centralizado do HttpClient.
 *
 * Garante para todas as requisições:
 * - URL base da API via token API_BASE_URL
 * - `withCredentials: true` (cookies de sessão)
 * - Header CSRF lido de cookie `XSRF-TOKEN` ou `<meta name="csrf-token">` (PLAY-08)
 */
@Injectable({ providedIn: 'root' })
export class ApiClientService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  /**
   * Lê token CSRF do cookie `XSRF-TOKEN` (prioridade)
   * ou do atributo `content` de `<meta name="csrf-token">` como fallback.
   */
  private csrfToken(): string | null {
    if (typeof document === 'undefined') return null;

    // 1. Cookie XSRF-TOKEN (definido pelo servidor em cada resposta)
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
    if (match?.[1]) {
      return decodeURIComponent(match[1]);
    }

    // 2. Meta tag fallback (para SSR / configurações alternativas)
    const meta = document.querySelector<HTMLMetaElement>('meta[name="csrf-token"]');
    return meta?.content ?? null;
  }

  private buildHeaders(): HttpHeaders {
    let headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    const token = this.csrfToken();
    if (token) {
      headers = headers.set('X-XSRF-TOKEN', token);
    }
    return headers;
  }

  private buildUrl(path: string): string {
    return `${this.baseUrl}${path}`;
  }

  get<T>(path: string, params?: Record<string, string>): Observable<T> {
    const httpParams = params ? new HttpParams({ fromObject: params }) : undefined;
    return this.http.get<T>(this.buildUrl(path), {
      headers: this.buildHeaders(),
      withCredentials: true,
      params: httpParams,
    });
  }

  post<T>(path: string, body: unknown): Observable<T> {
    return this.http.post<T>(this.buildUrl(path), body, {
      headers: this.buildHeaders(),
      withCredentials: true,
    });
  }

  put<T>(path: string, body: unknown): Observable<T> {
    return this.http.put<T>(this.buildUrl(path), body, {
      headers: this.buildHeaders(),
      withCredentials: true,
    });
  }

  delete<T>(path: string): Observable<T> {
    return this.http.delete<T>(this.buildUrl(path), {
      headers: this.buildHeaders(),
      withCredentials: true,
    });
  }
}

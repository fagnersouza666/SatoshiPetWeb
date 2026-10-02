import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApiClientService } from './api-client.service';
import { API_BASE_URL } from './api-config';
import { environment } from '../../environments/environment';

describe('ApiClientService: sessão e CSRF', () => {
  let api: ApiClientService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    api = TestBed.inject(ApiClientService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('inclui /api na URL de desenvolvimento', () => {
    expect(new URL(environment.apiUrl).pathname).toBe('/api');
  });

  it('captura X-CSRF-Token da API e o envia nas mutações seguintes', () => {
    api.post('/v1/auth/magic-link/verify', { token: 'fixture' }).subscribe();
    const login = http.expectOne('/api/v1/auth/magic-link/verify');
    expect(login.request.withCredentials).toBe(true);
    expect(login.request.headers.has('X-CSRF-Token')).toBe(false);
    login.flush({}, { headers: { 'X-CSRF-Token': 'server-token' } });
    for (const method of ['post', 'put', 'delete'] as const) {
      if (method === 'delete') api.delete('/v1/private').subscribe();
      else api[method]('/v1/private', {}).subscribe();
      const request = http.expectOne('/api/v1/private');
      expect(request.request.headers.get('X-CSRF-Token')).toBe('server-token');
      expect(request.request.headers.has('X-XSRF-TOKEN')).toBe(false);
      expect(request.request.withCredentials).toBe(true);
      request.flush({});
    }
  });

  it('substitui o CSRF antigo quando a recuperação cria outra sessão', () => {
    api.post('/v1/auth/register', {}).subscribe();
    http.expectOne('/api/v1/auth/register').flush({}, { headers: { 'X-CSRF-Token': 'old' } });
    api.post('/v1/account/recovery/reset', { code: 'fixture' }).subscribe();
    http
      .expectOne('/api/v1/account/recovery/reset')
      .flush({}, { headers: { 'X-CSRF-Token': 'new' } });
    api.post('/v1/auth/logout', {}).subscribe();
    const logout = http.expectOne('/api/v1/auth/logout');
    expect(logout.request.headers.get('X-CSRF-Token')).toBe('new');
    logout.flush(null);
  });
  it('limpa o CSRF ao encerrar a sessão', () => {
    api.post('/v1/auth/register', {}).subscribe();
    http.expectOne('/api/v1/auth/register').flush({}, { headers: { 'X-CSRF-Token': 'old' } });
    api.clearCsrfToken();
    api.post('/v1/auth/magic-link', {}).subscribe();
    const request = http.expectOne('/api/v1/auth/magic-link');
    expect(request.request.headers.has('X-CSRF-Token')).toBe(false);
    request.flush({});
  });

  it('preserva parâmetros e corpo de uma consulta com credenciais', () => {
    let result: unknown;
    api.get('/v1/query', { cursor: 'next' }).subscribe((body) => {
      result = body;
    });
    const request = http.expectOne('/api/v1/query?cursor=next');
    expect(request.request.withCredentials).toBe(true);
    request.flush({ items: [1] });
    expect(result).toEqual({ items: [1] });
  });
});

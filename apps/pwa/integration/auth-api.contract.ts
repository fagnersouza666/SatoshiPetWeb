/**
 * Contrato executado contra o HTTP real do Quarkus/H2 pelo PwaAuthContractTest.
 * Importa os serviços de produção; não replica sua implementação ou URLs.
 * Só navegação/cache e o transporte específico de browser são adaptados ao Node.
 */
import '@angular/compiler';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { Injector } from '@angular/core';
import {
  HttpBackend,
  HttpClient,
  HttpErrorResponse,
  HttpEvent,
  HttpHeaders,
  HttpRequest,
  HttpResponse,
} from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { ApiClientService } from '../src/app/core/api-client.service';
import { AuthService } from '../src/app/core/auth.service';
import { SessionService } from '../src/app/core/session.service';
import { PrivateCacheService } from '../src/app/core/private-cache.service';
import { API_BASE_URL } from '../src/app/core/api-config';
import { environment } from '../src/environments/environment';
import { environment as production } from '../src/environments/environment.prod';

type Fixture = { email: string; token: string; loginToken: string };
type Fixtures = { origin: string; address: string; users: Record<string, Fixture> };
const fixtures: Fixtures = JSON.parse(await readFile(process.argv[2], 'utf8'));
const apiPath = new URL(environment.apiUrl, fixtures.origin).pathname.replace(/\/$/, '');
const apiBase = `${fixtures.origin}${apiPath}`;

type Exchange = {
  method: string;
  path: string;
  url: string;
  cache?: RequestCache;
  body: unknown;
  headers: Headers;
  status: number;
  responseHeaders: Headers;
};

/** Cookie jar de uma origem; apenas para cookies efêmeros desta API de teste. */
class NetworkBackend implements HttpBackend {
  readonly cookies = new Map<string, string>();
  readonly exchanges: Exchange[] = [];
  tamperCsrf = false;
  failingOrigin: string | null = null;

  cookieHeader(): string {
    return [...this.cookies].map(([name, value]) => `${name}=${value}`).join('; ');
  }

  handle(request: HttpRequest<unknown>): Observable<HttpEvent<unknown>> {
    return new Observable((observer) => {
      const controller = new AbortController();
      const run = async () => {
        const headers = new Headers();
        for (const key of request.headers.keys()) headers.set(key, request.headers.get(key)!);
        // Equivalente ao withCredentials do browser; não inventa token CSRF.
        if (request.withCredentials && this.cookies.size)
          headers.set('Cookie', this.cookieHeader());
        if (this.tamperCsrf) headers.set('X-CSRF-Token', 'csrf-invalido-controlado');
        const url = this.failingOrigin
          ? new URL(new URL(request.urlWithParams).pathname, this.failingOrigin).href
          : request.urlWithParams;
        const response = await fetch(url, {
          method: request.method,
          headers,
          body: request.serializeBody() as BodyInit | null,
          signal: this.failingOrigin
            ? AbortSignal.any([controller.signal, AbortSignal.timeout(250)])
            : controller.signal,
          cache: request.cache,
          redirect: 'error',
        });
        if (request.withCredentials) {
          for (const cookie of response.headers.getSetCookie()) {
            const [pair] = cookie.split(';');
            const separator = pair.indexOf('=');
            const name = pair.slice(0, separator);
            const value = pair.slice(separator + 1);
            if (/max-age=0(?:;|$)/i.test(cookie)) this.cookies.delete(name);
            else this.cookies.set(name, value);
          }
        }
        const text = await response.text();
        let body: unknown = null;
        try {
          body = text ? JSON.parse(text) : null;
        } catch (error) {
          if (response.ok) {
            observer.error(
              new HttpErrorResponse({
                error: { error, text },
                status: response.status,
                url: request.urlWithParams,
              }),
            );
            return;
          }
          body = text;
        }
        const responseHeaders = new HttpHeaders(Object.fromEntries(response.headers.entries()));
        this.exchanges.push({
          method: request.method,
          path: new URL(request.urlWithParams).pathname,
          url: request.urlWithParams,
          cache: request.cache,
          body: request.body,
          headers,
          status: response.status,
          responseHeaders: response.headers,
        });
        if (!response.ok) {
          observer.error(
            new HttpErrorResponse({
              error: body,
              status: response.status,
              statusText: response.statusText,
              headers: responseHeaders,
              url: request.urlWithParams,
            }),
          );
          return;
        }
        observer.next(
          new HttpResponse({
            body,
            status: response.status,
            statusText: response.statusText,
            headers: responseHeaders,
            url: request.urlWithParams,
          }),
        );
        observer.complete();
      };
      run().catch((error) =>
        observer.error(new HttpErrorResponse({ error, status: 0, url: request.urlWithParams })),
      );
      return () => controller.abort();
    });
  }
}

const contexts: ReturnType<typeof browser>[] = [];
function browser() {
  const network = new NetworkBackend();
  const routes: unknown[][] = [];
  const cache = {
    clears: 0,
    async clearPrivateCaches() {
      this.clears++;
    },
  };
  const injector = Injector.create({
    providers: [
      AuthService,
      ApiClientService,
      SessionService,
      { provide: HttpClient, useValue: new HttpClient(network) },
      { provide: API_BASE_URL, useValue: apiBase },
      { provide: PrivateCacheService, useValue: cache },
      {
        provide: Router,
        useValue: {
          async navigate(commands: unknown[]) {
            routes.push(commands);
            return true;
          },
        },
      },
    ],
  });
  const context = {
    network,
    routes,
    cache,
    injector,
    auth: injector.get(AuthService),
    api: injector.get(ApiClientService),
    session: injector.get(SessionService),
  };
  contexts.push(context);
  return context;
}

async function rejectsStatus(operation: Promise<unknown>, status: number) {
  await assert.rejects(operation, (error: unknown) => {
    assert.ok(error instanceof HttpErrorResponse, 'Erro deve preservar o contrato Angular');
    assert.equal(error.status, status, 'Status HTTP real deve corresponder ao esperado');
    return true;
  });
}

async function signup(name: string) {
  const context = browser();
  const user = fixtures.users[name];
  const verified = await context.auth.verifyAndNavigate(user.token);
  assert.deepEqual(verified, { email: user.email, isNewUser: true });
  assert.equal(context.session.isAuthenticated(), false);
  assert.deepEqual(context.routes.at(-1), ['/cadastro']);
  assert.deepEqual(context.auth.pendingVerify(), verified);
  const account = await firstValueFrom(
    context.auth.register({ address: fixtures.address, petName: 'Pet contrato' }),
  );
  assert.equal(account.email, user.email);
  assert.equal(account.address, fixtures.address);
  assert.equal(account.petName, 'Pet contrato');
  assert.ok(account.id);
  assert.equal(context.auth.pendingVerify(), null);
  // O componente de cadastro aplica a sessão retornada pelo serviço.
  context.session.setSession(account);
  const register = context.network.exchanges.find((entry) =>
    entry.path.endsWith('/auth/register'),
  )!;
  assert.equal(register.status, 201);
  assert.deepEqual(register.body, {
    token: user.token,
    bitcoinAddress: fixtures.address,
    petName: 'Pet contrato',
  });
  assert.ok(
    context.network.exchanges.some(
      (entry) => entry.path.endsWith('/account/me') && entry.status === 200,
    ),
  );
  const accountRead = context.network.exchanges.find((entry) =>
    entry.path.endsWith('/account/me'),
  )!;
  assert.equal(new URL(accountRead.url).searchParams.get('ngsw-bypass'), 'true');
  assert.equal(accountRead.cache, 'no-store');
  assert.ok(context.network.cookies.has('sp_session'));
  return context;
}

async function direct(path: string, cookie: string, init: RequestInit = {}) {
  return fetch(`${fixtures.origin}/api${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      Cookie: cookie,
      ...init.headers,
    },
  });
}

let failures = 0;
async function test(name: string, run: () => Promise<void>) {
  try {
    await run();
    console.log(`OK: ${name}`);
  } catch (error) {
    failures++;
    console.error(`FALHA: ${name}`);
    console.error(error);
  }
}

await test('bases de desenvolvimento e produção preservam /api; solicitação real retorna 202', async () => {
  assert.equal(apiPath, '/api');
  assert.equal(new URL(production.apiUrl, fixtures.origin).pathname.replace(/\/$/, ''), '/api');
  const context = browser();
  await firstValueFrom(context.auth.requestMagicLink(`request-${Date.now()}@example.invalid`));
  assert.equal(context.network.exchanges.at(-1)?.status, 202);
});

await test('cadastro usa token + bitcoinAddress; lê conta e rejeita token consumido', async () => {
  const context = await signup('signup');
  const user = fixtures.users.signup;
  await rejectsStatus(
    firstValueFrom(
      context.api.post('/v1/auth/register', {
        token: user.token,
        bitcoinAddress: fixtures.address,
        petName: 'Pet contrato',
      }),
    ),
    422,
  );
  await rejectsStatus(browser().auth.verifyAndNavigate(user.token), 401);
  await assert.rejects(
    firstValueFrom(context.auth.register({ address: fixtures.address, petName: 'Pet contrato' })),
  );
});

await test('magic link inválido não autentica nem navega', async () => {
  const context = browser();
  await rejectsStatus(context.auth.verifyAndNavigate('magic-link-invalido-controlado'), 401);
  assert.equal(context.session.isAuthenticated(), false);
  assert.equal(context.auth.pendingVerify(), null);
  assert.deepEqual(context.routes, []);
});

await test('login existente lê /account/me e consome o magic link', async () => {
  const registered = await signup('existing');
  const context = browser();
  const user = fixtures.users.existing;
  assert.deepEqual(await context.auth.verifyAndNavigate(user.loginToken), {
    email: user.email,
    isNewUser: false,
  });
  assert.deepEqual(context.session.account(), registered.session.account());
  assert.deepEqual(context.routes.at(-1), ['/conta']);
  assert.equal(context.auth.pendingVerify(), null);
  await rejectsStatus(browser().auth.verifyAndNavigate(user.loginToken), 401);
  const generated = await firstValueFrom(
    context.api.post<{ code: string }>('/v1/account/recovery/code', {}),
  );
  assert.ok(generated.code, 'login deve capturar o header CSRF da sessão criada');
});

await test('CSRF ausente/incorreto é rejeitado; ApiClient envia o header real com sucesso', async () => {
  const context = await signup('csrf');
  const cookie = context.network.cookieHeader();
  const route = '/v1/account/recovery/code';
  assert.equal((await direct(route, cookie, { method: 'POST', body: '{}' })).status, 403);
  assert.equal(
    (
      await direct(route, cookie, {
        method: 'POST',
        body: '{}',
        headers: { 'X-CSRF-Token': 'invalido' },
      })
    ).status,
    403,
  );
  const registered = context.network.exchanges.find((entry) =>
    entry.path.endsWith('/auth/register'),
  )!;
  const csrf = registered.responseHeaders.get('X-CSRF-Token');
  assert.ok(csrf);
  assert.equal(context.network.cookies.has('XSRF-TOKEN'), false);
  assert.equal(
    (
      await direct(route, cookie, {
        method: 'POST',
        body: '{}',
        headers: { 'X-XSRF-TOKEN': csrf! },
      })
    ).status,
    403,
  );
  const generated = await firstValueFrom(context.api.post<{ code: string }>(route, {}));
  assert.ok(generated.code);
  assert.equal(context.network.exchanges.at(-1)?.headers.get('X-CSRF-Token'), csrf);
});

await test('recuperação real cria sessão, revoga antiga e rejeita código usado/inválido', async () => {
  const previous = await signup('recovery');
  const oldCookie = previous.network.cookieHeader();
  const { code } = await firstValueFrom(
    previous.api.post<{ code: string }>('/v1/account/recovery/code', {}),
  );
  const recovered = browser();
  await recovered.auth.recoverAndNavigate(code);
  assert.deepEqual(recovered.session.account(), previous.session.account());
  assert.deepEqual(recovered.routes.at(-1), ['/conta']);
  assert.equal((await direct('/v1/account/me', oldCookie)).status, 401);
  assert.equal(
    (await firstValueFrom(recovered.api.get<{ email: string }>('/v1/account/me'))).email,
    fixtures.users.recovery.email,
  );
  const rejected = browser();
  await rejectsStatus(rejected.auth.recoverAndNavigate(code), 401);
  await rejectsStatus(rejected.auth.recoverAndNavigate('codigo-invalido-controlado'), 401);
  assert.equal(rejected.session.isAuthenticated(), false);
  assert.deepEqual(rejected.routes, []);
  assert.ok(
    (await firstValueFrom(recovered.api.post<{ code: string }>('/v1/account/recovery/code', {})))
      .code,
  );
});

await test('logout revoga no servidor; repetição 401 limpa a sessão local', async () => {
  const context = await signup('logout');
  const cookie = context.network.cookieHeader();
  await context.auth.logout();
  assert.equal(context.network.exchanges.at(-1)?.status, 204);
  assert.equal(context.session.isAuthenticated(), false);
  assert.equal(context.cache.clears, 1);
  assert.deepEqual(context.routes.at(-1), ['/entrar']);
  assert.equal(context.network.cookies.has('sp_session'), false);
  assert.equal((await direct('/v1/account/me', cookie)).status, 401);
  await context.auth.logout();
  assert.equal(context.network.exchanges.at(-1)?.status, 401);
  assert.equal(context.cache.clears, 2);
});

await test('logout 403 não finge sucesso nem limpa conta/cache; sessão segue válida', async () => {
  const context = await signup('forbidden');
  const account = context.session.account();
  const routes = context.routes.length;
  context.network.tamperCsrf = true;
  await rejectsStatus(context.auth.logout(), 403);
  assert.deepEqual(context.session.account(), account);
  assert.equal(context.cache.clears, 0);
  assert.equal(context.routes.length, routes);
  assert.equal((await direct('/v1/account/me', context.network.cookieHeader())).status, 200);
  context.network.tamperCsrf = false;
  await context.auth.logout();
  assert.equal(context.session.isAuthenticated(), false);
});

await test('falha real de rede no logout preserva conta/cache e permite nova tentativa', async () => {
  const context = await signup('network');
  // Conexão HTTP real que não responde: timeout de transporte, sem erro simulado.
  // Manter a porta reservada evita uma corrida com outro processo que a reutilize.
  const server = createServer(() => {});
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = server.address();
  assert.ok(address && typeof address !== 'string');
  try {
    context.network.failingOrigin = `http://127.0.0.1:${address.port}`;
    await rejectsStatus(context.auth.logout(), 0);
    assert.equal(context.session.isAuthenticated(), true);
    assert.equal(context.cache.clears, 0);
    assert.equal((await direct('/v1/account/me', context.network.cookieHeader())).status, 200);
    context.network.failingOrigin = null;
    await context.auth.logout();
    assert.equal(context.session.isAuthenticated(), false);
  } finally {
    server.closeAllConnections();
    await new Promise<void>((resolve, reject) =>
      server.close((error) => (error ? reject(error) : resolve())),
    );
  }
});

for (const context of contexts) context.injector.destroy();
console.log(`Contrato PWA/API: ${9 - failures}/9 cenários aprovados`);
process.exitCode = failures ? 1 : 0;

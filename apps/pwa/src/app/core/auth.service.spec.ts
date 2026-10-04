import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from './auth.service';
import { API_BASE_URL } from './api-config';
import { SessionService } from './session.service';
import { ApiClientService } from './api-client.service';
import { PrivateCacheService } from './private-cache.service';

const accountResponse = {
  id: 'account-1',
  email: 'teste@example.invalid',
  bitcoinAddress: 'bc1qteste',
  petName: 'Satoshi',
};
const account = {
  id: 'account-1',
  email: 'teste@example.invalid',
  address: 'bc1qteste',
  petName: 'Satoshi',
};

describe('AuthService: contratos HTTP reais da API', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let session: SessionService;
  let navigate: ReturnType<typeof vi.spyOn>;
  let clearCaches: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    TestBed.resetTestingModule();
    clearCaches = vi.fn().mockResolvedValue(undefined);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
        { provide: PrivateCacheService, useValue: { clearPrivateCaches: clearCaches } },
      ],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionService);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });
  afterEach(() => http.verify());

  it('solicita magic-link na rota pública', async () => {
    const result = firstValueFrom(service.requestMagicLink('teste@example.invalid'));
    const request = http.expectOne('/api/v1/auth/magic-link');
    expect(request.request.body).toEqual({ email: 'teste@example.invalid' });
    request.flush(null, { status: 202, statusText: 'Accepted' });
    await result;
  });

  it('traduz registrationRequired/verifiedEmail e preserva o token para cadastro', async () => {
    const result = service.verifyAndNavigate('new-token');
    http
      .expectOne('/api/v1/auth/magic-link/verify')
      .flush({ status: 'ok', registrationRequired: true, verifiedEmail: account.email });
    await expect(result).resolves.toEqual({ email: account.email, isNewUser: true });
    expect(service.pendingVerify()).toEqual({ email: account.email, isNewUser: true });
    expect(session.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/cadastro']);
    const registered = firstValueFrom(
      service.register({ address: account.address, petName: account.petName }),
    );
    const request = http.expectOne('/api/v1/auth/register');
    expect(request.request.body).toEqual({
      token: 'new-token',
      bitcoinAddress: account.address,
      petName: account.petName,
    });
    request.flush(
      { status: 'ok', accountId: account.id },
      { status: 201, statusText: 'Created', headers: { 'X-CSRF-Token': 'csrf-registration' } },
    );
    http.expectOne('/api/v1/account/me?ngsw-bypass=true').flush(accountResponse);
    await expect(registered).resolves.toEqual(account);
    expect(service.pendingVerify()).toBeNull();
  });

  it('busca dados reais da conta após verificar usuário existente', async () => {
    const result = service.verifyAndNavigate('existing-token');
    http
      .expectOne('/api/v1/auth/magic-link/verify')
      .flush(
        { status: 'ok', registrationRequired: false, verifiedEmail: account.email },
        { headers: { 'X-CSRF-Token': 'csrf-login' } },
      );
    await Promise.resolve();
    const fresh = http.expectOne('/api/v1/account/me?ngsw-bypass=true');
    expect(fresh.request.cache).toBe('no-store');
    fresh.flush(accountResponse);
    await result;
    expect(session.account()).toEqual(account);
    expect(service.pendingVerify()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/conta']);
  });

  it('rejeita token inválido sem criar sessão ou navegar', async () => {
    const result = service.verifyAndNavigate('invalid');
    const rejected = expect(result).rejects.toMatchObject({ status: 401 });
    http
      .expectOne('/api/v1/auth/magic-link/verify')
      .flush({ code: 'unauthorized' }, { status: 401, statusText: 'Unauthorized' });
    await rejected;
    expect(session.isAuthenticated()).toBe(false);
    expect(service.pendingVerify()).toBeNull();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('não envia cadastro sem verificação pendente', async () => {
    let failure: unknown;
    service.register({ address: account.address, petName: account.petName }).subscribe({
      error: (error: unknown) => {
        failure = error;
      },
    });
    http.expectNone('/api/v1/auth/register');
    expect(failure).toBeInstanceOf(Error);
  });

  it('logout confirmado limpa cache antes da sessão e navegação', async () => {
    session.setSession(account);
    clearCaches.mockImplementation(async () => expect(session.isAuthenticated()).toBe(true));
    const result = service.logout();
    expect(session.isAuthenticated()).toBe(true);
    http.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    await result;
    expect(clearCaches).toHaveBeenCalledOnce();
    expect(session.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/entrar']);
  });

  it.each([0, 403, 500])('logout com falha %s preserva sessão e informa erro', async (status) => {
    session.setSession(account);
    const result = service.logout();
    const rejected = expect(result).rejects.toMatchObject({ status });
    const request = http.expectOne('/api/v1/auth/logout');
    if (status === 0) request.error(new ProgressEvent('error'));
    else request.flush({}, { status, statusText: 'Failure' });
    await rejected;
    expect(session.account()).toEqual(account);
    expect(clearCaches).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('logout401 já revogado permite a limpeza local', async () => {
    session.setSession(account);
    const result = service.logout();
    http.expectOne('/api/v1/auth/logout').flush({}, { status: 401, statusText: 'Unauthorized' });
    await result;
    expect(session.isAuthenticated()).toBe(false);
    expect(clearCaches).toHaveBeenCalledOnce();
  });
  it('recupera pelo código real, carrega a conta e navega', async () => {
    const result = service.recoverAccess('recovery-fixture', 'email-token');
    const request = http.expectOne('/api/v1/account/recovery/reset');
    expect(request.request.body).toEqual({ code: 'recovery-fixture', token: 'email-token' });
    request.flush(
      { status: 'ok', recoveryCode: 'new-recovery-code' },
      { headers: { 'X-CSRF-Token': 'csrf-recovery' } },
    );
    await expect(result).resolves.toBe('new-recovery-code');
    expect(navigate).not.toHaveBeenCalled();
    const opened = service.openRecoveredAccount();
    const fresh = http.expectOne('/api/v1/account/me?ngsw-bypass=true');
    expect(fresh.request.cache).toBe('no-store');
    fresh.flush(accountResponse);
    await opened;
    expect(session.account()).toEqual(account);
    expect(navigate).toHaveBeenCalledWith(['/conta']);
  });

  it('não navega nem cria sessão com código de recuperação rejeitado', async () => {
    const result = service.recoverAccess('used-code', 'email-token');
    const rejected = expect(result).rejects.toMatchObject({ status: 401 });
    http
      .expectOne('/api/v1/account/recovery/reset')
      .flush({ code: 'invalid_code' }, { status: 401, statusText: 'Unauthorized' });
    await rejected;
    expect(session.isAuthenticated()).toBe(false);
    expect(navigate).not.toHaveBeenCalled();
    http.expectNone('/api/v1/account/me?ngsw-bypass=true');
  });

  it('remove o token pendente anterior se uma nova verificação falhar', async () => {
    const first = service.verifyAndNavigate('first-token');
    http
      .expectOne('/api/v1/auth/magic-link/verify')
      .flush({ registrationRequired: true, verifiedEmail: account.email });
    await first;
    const next = service.verifyAndNavigate('invalid-next');
    const rejected = expect(next).rejects.toMatchObject({ status: 401 });
    http
      .expectOne('/api/v1/auth/magic-link/verify')
      .flush({}, { status: 401, statusText: 'Unauthorized' });
    await rejected;
    expect(service.pendingVerify()).toBeNull();
    await expect(
      firstValueFrom(service.register({ address: account.address, petName: account.petName })),
    ).rejects.toThrow();
    http.expectNone('/api/v1/auth/register');
  });

  it('mantém token de cadastro quando os dados são rejeitados para permitir correção', async () => {
    const first = service.verifyAndNavigate('registration-token');
    http
      .expectOne('/api/v1/auth/magic-link/verify')
      .flush({ registrationRequired: true, verifiedEmail: account.email });
    await first;
    const result = firstValueFrom(
      service.register({ address: 'invalid', petName: account.petName }),
    );
    const rejected = expect(result).rejects.toMatchObject({ status: 422 });
    http
      .expectOne('/api/v1/auth/register')
      .flush({ code: 'invalid_address' }, { status: 422, statusText: 'Unprocessable Entity' });
    await rejected;
    expect(service.pendingVerify()?.isNewUser).toBe(true);
    const retry = firstValueFrom(
      service.register({ address: account.address, petName: account.petName }),
    );
    const request = http.expectOne('/api/v1/auth/register');
    expect(request.request.body.token).toBe('registration-token');
    request.flush({ status: 'ok', accountId: account.id });
    http.expectOne('/api/v1/account/me?ngsw-bypass=true').flush(accountResponse);
    await retry;
    await expect(
      firstValueFrom(service.register({ address: account.address, petName: account.petName })),
    ).rejects.toThrow();
    http.expectNone('/api/v1/auth/register');
  });

  it.each(['verify', 'register'] as const)(
    'retoma %s sem reutilizar segredo consumido após falha temporária de /me',
    async (flow) => {
      if (flow === 'register') {
        const verified = service.verifyAndNavigate('registration-fixture');
        http
          .expectOne('/api/v1/auth/magic-link/verify')
          .flush({ registrationRequired: true, verifiedEmail: account.email });
        await verified;
      }
      const action = () =>
        flow === 'verify'
          ? service.verifyAndNavigate('login-fixture')
          : firstValueFrom(
              service.register({ address: account.address, petName: account.petName }),
            );
      const path = flow === 'verify' ? '/api/v1/auth/magic-link/verify' : '/api/v1/auth/register';
      const first = action();
      const rejected = expect(first).rejects.toMatchObject({ status: 503 });
      http
        .expectOne(path)
        .flush(
          flow === 'verify'
            ? { registrationRequired: false, verifiedEmail: account.email }
            : { status: 'ok', accountId: account.id },
        );
      await Promise.resolve();
      http
        .expectOne('/api/v1/account/me?ngsw-bypass=true')
        .flush({}, { status: 503, statusText: 'Unavailable' });
      await rejected;
      const retry = action();
      http.expectNone(path);
      http.expectOne('/api/v1/account/me?ngsw-bypass=true').flush(accountResponse);
      await retry;
      expect(service.pendingVerify()).toBeNull();
      if (flow !== 'register') expect(session.account()).toEqual(account);
    },
  );
  it('restaura a identidade e o CSRF depois de recarregar a aplicação', async () => {
    const result = service.restoreSession();
    const restored = http.expectOne('/api/v1/account/me?ngsw-bypass=true');
    expect(restored.request.cache).toBe('no-store');
    restored.flush(accountResponse, { headers: { 'X-CSRF-Token': 'restored-csrf' } });
    await result;
    expect(session.account()).toEqual(account);
    const logout = service.logout();
    const request = http.expectOne('/api/v1/auth/logout');
    expect(request.request.headers.get('X-CSRF-Token')).toBe('restored-csrf');
    request.flush(null);
    await logout;
  });

  it.each([0, 401])(
    'bootstrap sem resposta autenticada (%s) não inventa identidade offline',
    async (status) => {
      const result = service.restoreSession();
      const request = http.expectOne('/api/v1/account/me?ngsw-bypass=true');
      if (status === 0) request.error(new ProgressEvent('error'));
      else request.flush({}, { status, statusText: 'Unauthorized' });
      await result;
      expect(session.isAuthenticated()).toBe(false);
    },
  );

  it('401 de leitura privada remove identidade e caches antigos', async () => {
    session.setSession(account);
    const result = firstValueFrom(TestBed.inject(ApiClientService).getFresh('/v1/account/pet'));
    const rejected = expect(result).rejects.toMatchObject({ status: 401 });
    http
      .expectOne('/api/v1/account/pet?ngsw-bypass=true')
      .flush({}, { status: 401, statusText: 'Unauthorized' });
    await rejected;
    expect(session.isAuthenticated()).toBe(false);
    expect(clearCaches).toHaveBeenCalled();
  });

  it('solicita verificação do novo e-mail sem consumir o código de recuperação', async () => {
    const result = service.requestRecoveryEmail('backup-code', 'new@example.invalid');
    const request = http.expectOne('/api/v1/account/recovery/email');
    expect(request.request.body).toEqual({ code: 'backup-code', email: 'new@example.invalid' });
    request.flush(null, { status: 202, statusText: 'Accepted' });
    await result;
    expect(session.isAuthenticated()).toBe(false);
  });

  it('permite repetir abertura de conta após recuperação sem consumir novamente os segredos', async () => {
    const recovered = service.recoverAccess('backup-code', 'verified-token');
    http
      .expectOne('/api/v1/account/recovery/reset')
      .flush({ status: 'ok', recoveryCode: 'replacement-code' });
    await expect(recovered).resolves.toBe('replacement-code');
    const opened = service.openRecoveredAccount();
    const rejected = expect(opened).rejects.toMatchObject({ status: 503 });
    http
      .expectOne('/api/v1/account/me?ngsw-bypass=true')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    await rejected;
    const retry = service.openRecoveredAccount();
    http.expectNone('/api/v1/account/recovery/reset');
    http.expectOne('/api/v1/account/me?ngsw-bypass=true').flush(accountResponse);
    await retry;
    expect(session.account()).toEqual(account);
  });
});

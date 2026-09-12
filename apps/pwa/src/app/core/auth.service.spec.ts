import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { ApiClientService } from './api-client.service';
import { SessionService } from './session.service';
import { PrivateCacheService } from './private-cache.service';

describe('AuthService', () => {
  let service: AuthService;
  let apiSpy: { post: ReturnType<typeof vi.fn> };
  let sessionSpy: { setSession: ReturnType<typeof vi.fn>; clearSession: ReturnType<typeof vi.fn>; isAuthenticated: ReturnType<typeof vi.fn> };
  let cacheSpy: { clearPrivateCaches: ReturnType<typeof vi.fn> };
  let router: Router;

  beforeEach(() => {
    apiSpy = { post: vi.fn() };
    sessionSpy = {
      setSession: vi.fn(),
      clearSession: vi.fn(),
      isAuthenticated: vi.fn().mockReturnValue(false),
    };
    cacheSpy = { clearPrivateCaches: vi.fn().mockResolvedValue(undefined) };

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ApiClientService, useValue: apiSpy },
        { provide: SessionService, useValue: sessionSpy },
        { provide: PrivateCacheService, useValue: cacheSpy },
      ],
    });

    service = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  it('deve ser criado', () => {
    expect(service).toBeTruthy();
  });

  it('requestMagicLink deve chamar POST /v1/auth/magic-link com o e-mail', () => {
    apiSpy.post.mockReturnValue(of(undefined));
    service.requestMagicLink('teste@satoshi.pet').subscribe();
    expect(apiSpy.post).toHaveBeenCalledWith('/v1/auth/magic-link', { email: 'teste@satoshi.pet' });
  });

  it('verifyToken deve chamar POST /v1/auth/verify com o token', () => {
    const mockResp = { email: 'x@y.com', isNewUser: false };
    apiSpy.post.mockReturnValue(of(mockResp));
    service.verifyToken('tok123').subscribe();
    expect(apiSpy.post).toHaveBeenCalledWith('/v1/auth/verify', { token: 'tok123' });
  });

  it('register deve chamar POST /v1/auth/register com o payload', () => {
    const payload = { address: 'bc1q123', petName: 'Satoshi' };
    const mockAccount = { id: '1', email: 'x@y.com', ...payload };
    apiSpy.post.mockReturnValue(of(mockAccount));
    service.register(payload).subscribe();
    expect(apiSpy.post).toHaveBeenCalledWith('/v1/auth/register', payload);
  });

  it('logout deve limpar cache, sessão e navegar para /entrar', async () => {
    apiSpy.post.mockReturnValue(of(undefined));
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    await service.logout();

    expect(cacheSpy.clearPrivateCaches).toHaveBeenCalled();
    expect(sessionSpy.clearSession).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(['/entrar']);
  });

  it('logout deve continuar mesmo que a chamada ao servidor falhe', async () => {
    apiSpy.post.mockReturnValue(throwError(() => new Error('Sessão expirada')));
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    await service.logout();

    expect(cacheSpy.clearPrivateCaches).toHaveBeenCalled();
    expect(sessionSpy.clearSession).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(['/entrar']);
  });

  it('pendingVerify deve inicializar como null', () => {
    expect(service.pendingVerify()).toBeNull();
  });

  it('verifyAndNavigate deve definir pendingVerify e navegar para /cadastro quando isNewUser=true', async () => {
    apiSpy.post.mockReturnValue(of({ email: 'novo@x.com', isNewUser: true }));
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    await service.verifyAndNavigate('token-novo');

    expect(service.pendingVerify()).toEqual({ email: 'novo@x.com', isNewUser: true });
    expect(navigateSpy).toHaveBeenCalledWith(['/cadastro']);
  });

  it('verifyAndNavigate deve chamar setSession e navegar para /conta quando isNewUser=false', async () => {
    const account = { id: '1', email: 'existente@x.com' };
    apiSpy.post.mockReturnValue(of({ email: 'existente@x.com', isNewUser: false, account }));
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    await service.verifyAndNavigate('token-existente');

    expect(sessionSpy.setSession).toHaveBeenCalledWith(account);
    expect(navigateSpy).toHaveBeenCalledWith(['/conta']);
  });
});

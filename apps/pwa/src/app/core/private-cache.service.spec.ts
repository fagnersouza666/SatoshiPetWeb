import { TestBed } from '@angular/core/testing';
import { PrivateCacheService } from './private-cache.service';

describe('PrivateCacheService', () => {
  let service: PrivateCacheService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(PrivateCacheService);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('deve ser criado', () => {
    expect(service).toBeTruthy();
  });

  it('deve completar sem erro quando Cache API está indisponível', async () => {
    // Simula ambiente sem Cache API (ex.: contexto não-HTTPS em testes)
    const originalCaches = (globalThis as Record<string, unknown>)['caches'];
    delete (globalThis as Record<string, unknown>)['caches'];

    await expect(service.clearPrivateCaches()).resolves.toBeUndefined();

    // Restaurar
    if (originalCaches !== undefined) {
      (globalThis as Record<string, unknown>)['caches'] = originalCaches;
    }
  });

  it('deve deletar o bucket privado inteiro', async () => {
    const deleteSpy = vi.fn().mockResolvedValue(true);
    const keysSpy = vi
      .fn()
      .mockResolvedValue([
        'private-account-data',
        'ngsw:db:/api/v1/account:responses',
        'ngsw:db:/api/v1/auth:responses',
        'public-assets-cache',
      ]);
    const openSpy = vi.fn().mockResolvedValue({
      keys: vi.fn().mockResolvedValue([]),
      delete: vi.fn(),
    });
    vi.stubGlobal('caches', { keys: keysSpy, delete: deleteSpy, open: openSpy });

    await service.clearPrivateCaches();

    expect(deleteSpy).toHaveBeenCalledWith('private-account-data');
    expect(deleteSpy).toHaveBeenCalledWith('ngsw:db:/api/v1/account:responses');
    expect(deleteSpy).toHaveBeenCalledWith('ngsw:db:/api/v1/auth:responses');
    expect(deleteSpy).not.toHaveBeenCalledWith('public-assets-cache');
    expect(openSpy).toHaveBeenCalledWith('public-assets-cache');
  });

  it('deve deletar entradas privadas do bucket compartilhado do ngsw', async () => {
    const deleteSpy = vi.fn().mockResolvedValue(true);
    const requests = [
      new Request('https://satoshi.pet/api/v1/account/me'),
      new Request('https://satoshi.pet/api/v1/account/pet/name'),
      new Request('https://satoshi.pet/api/v1/public/addresses/bc1qexample'),
    ];
    const keysSpy = vi.fn().mockResolvedValue(requests);
    const cache = { keys: keysSpy, delete: deleteSpy };
    const openSpy = vi.fn().mockResolvedValue(cache);
    vi.stubGlobal('caches', {
      keys: vi.fn().mockResolvedValue(['ngsw:db:version:api-freshness']),
      open: openSpy,
      delete: vi.fn(),
    });

    await service.clearPrivateCaches();

    expect(openSpy).toHaveBeenCalledWith('ngsw:db:version:api-freshness');
    expect(deleteSpy).toHaveBeenCalledTimes(2);
    expect(deleteSpy).toHaveBeenCalledWith(requests[0]);
    expect(deleteSpy).toHaveBeenCalledWith(requests[1]);
    expect(deleteSpy).not.toHaveBeenCalledWith(requests[2]);
  });

  it('deve preservar entradas públicas e caminhos parecidos', async () => {
    const deleteSpy = vi.fn().mockResolvedValue(true);
    const publicRequest = new Request('https://satoshi.pet/api/v1/public/addresses/bc1qexample');
    const similarPathRequest = new Request('https://satoshi.pet/api/v1/accounting/report');
    const cache = {
      keys: vi.fn().mockResolvedValue([publicRequest, similarPathRequest]),
      delete: deleteSpy,
    };
    vi.stubGlobal('caches', {
      keys: vi.fn().mockResolvedValue(['public-api-cache']),
      open: vi.fn().mockResolvedValue(cache),
      delete: vi.fn(),
    });

    await service.clearPrivateCaches();

    expect(deleteSpy).not.toHaveBeenCalled();
  });

  it('deve continuar mesmo que um delete de cache falhe', async () => {
    const deleteSpy = vi
      .fn()
      .mockRejectedValueOnce(new Error('Falha ao deletar'))
      .mockResolvedValue(true);

    const requests = [
      new Request('https://satoshi.pet/api/v1/account/me'),
      new Request('https://satoshi.pet/api/v1/account/pet/name'),
    ];
    const cache = {
      keys: vi.fn().mockResolvedValue(requests),
      delete: deleteSpy,
    };
    vi.stubGlobal('caches', {
      keys: vi.fn().mockResolvedValue(['ngsw:db:version:api-freshness']),
      open: vi.fn().mockResolvedValue(cache),
      delete: vi.fn(),
    });

    await expect(service.clearPrivateCaches()).resolves.toBeUndefined();
    expect(deleteSpy).toHaveBeenCalledTimes(2);
  });

  it('deve continuar quando a leitura de um bucket falha', async () => {
    const secondDeleteSpy = vi.fn().mockResolvedValue(true);
    const privateRequest = new Request('https://satoshi.pet/api/v1/auth/session');
    vi.stubGlobal('caches', {
      keys: vi.fn().mockResolvedValue(['broken-cache', 'api-cache']),
      open: vi
        .fn()
        .mockRejectedValueOnce(new Error('Falha ao abrir'))
        .mockResolvedValueOnce({
          keys: vi.fn().mockResolvedValue([privateRequest]),
          delete: secondDeleteSpy,
        }),
      delete: vi.fn(),
    });

    await expect(service.clearPrivateCaches()).resolves.toBeUndefined();
    expect(secondDeleteSpy).toHaveBeenCalledWith(privateRequest);
  });
});

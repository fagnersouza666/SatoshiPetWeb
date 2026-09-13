import { TestBed } from '@angular/core/testing';
import { PrivateCacheService } from './private-cache.service';

describe('PrivateCacheService', () => {
  let service: PrivateCacheService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(PrivateCacheService);
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

  it('deve deletar caches com nome contendo "/api/v1/conta"', async () => {
    const deleteSpy = vi.fn().mockResolvedValue(true);
    const keysSpy = vi
      .fn()
      .mockResolvedValue([
        'ngsw:db:/api/v1/conta:responses',
        'ngsw:db:/api/v1/auth:responses',
        'public-assets-cache',
      ]);

    Object.defineProperty(globalThis, 'caches', {
      value: { keys: keysSpy, delete: deleteSpy },
      writable: true,
      configurable: true,
    });

    await service.clearPrivateCaches();

    expect(deleteSpy).toHaveBeenCalledWith('ngsw:db:/api/v1/conta:responses');
    expect(deleteSpy).toHaveBeenCalledWith('ngsw:db:/api/v1/auth:responses');
    expect(deleteSpy).not.toHaveBeenCalledWith('public-assets-cache');
  });

  it('deve deletar caches com nome contendo "private"', async () => {
    const deleteSpy = vi.fn().mockResolvedValue(true);
    const keysSpy = vi.fn().mockResolvedValue(['private-account-data', 'public-icons']);

    Object.defineProperty(globalThis, 'caches', {
      value: { keys: keysSpy, delete: deleteSpy },
      writable: true,
      configurable: true,
    });

    await service.clearPrivateCaches();

    expect(deleteSpy).toHaveBeenCalledWith('private-account-data');
    expect(deleteSpy).not.toHaveBeenCalledWith('public-icons');
  });

  it('deve não falhar quando não há caches privados', async () => {
    const deleteSpy = vi.fn().mockResolvedValue(true);
    const keysSpy = vi.fn().mockResolvedValue(['public-assets', 'ngsw:db:public']);

    Object.defineProperty(globalThis, 'caches', {
      value: { keys: keysSpy, delete: deleteSpy },
      writable: true,
      configurable: true,
    });

    await service.clearPrivateCaches();

    expect(deleteSpy).not.toHaveBeenCalled();
  });

  it('deve continuar mesmo que um delete de cache falhe', async () => {
    const deleteSpy = vi
      .fn()
      .mockRejectedValueOnce(new Error('Falha ao deletar'))
      .mockResolvedValue(true);

    const keysSpy = vi
      .fn()
      .mockResolvedValue(['ngsw:db:/api/v1/conta:resp1', 'ngsw:db:/api/v1/conta:resp2']);

    Object.defineProperty(globalThis, 'caches', {
      value: { keys: keysSpy, delete: deleteSpy },
      writable: true,
      configurable: true,
    });

    await expect(service.clearPrivateCaches()).resolves.toBeUndefined();
    expect(deleteSpy).toHaveBeenCalledTimes(2);
  });

  it('deve preservar sprites de duas criaturas no cache público ao limpar dados privados', async () => {
    const sprites = new Map([
      ['/assets/criatura-a/sprite.png', 'sprite-a'],
      ['/assets/criatura-b/sprite.png', 'sprite-b'],
    ]);
    const deleteSpy = vi.fn((name: string) => {
      if (name === 'public-assets-cache') {
        sprites.clear();
      }
      return Promise.resolve(true);
    });
    const keysSpy = vi.fn().mockResolvedValue(['private-account-data', 'public-assets-cache']);

    Object.defineProperty(globalThis, 'caches', {
      value: { keys: keysSpy, delete: deleteSpy },
      writable: true,
      configurable: true,
    });

    await service.clearPrivateCaches();

    expect(deleteSpy).toHaveBeenCalledWith('private-account-data');
    expect(deleteSpy).not.toHaveBeenCalledWith('public-assets-cache');
    expect(sprites.get('/assets/criatura-a/sprite.png')).toBe('sprite-a');
    expect(sprites.get('/assets/criatura-b/sprite.png')).toBe('sprite-b');
  });
});

import { TestBed } from '@angular/core/testing';
import { OfflineService } from './offline.service';

describe('OfflineService', () => {
  let service: OfflineService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(OfflineService);
  });

  afterEach(() => {
    // Garantir estado consistente entre testes
    window.dispatchEvent(new Event('online'));
  });

  it('deve ser criado', () => {
    expect(service).toBeTruthy();
  });

  it('deve inicializar como online quando navigator.onLine = true', () => {
    // jsdom define navigator.onLine = true por padrão
    expect(service.isOffline()).toBe(false);
  });

  it('deve atualizar isOffline para true ao receber evento "offline"', () => {
    window.dispatchEvent(new Event('offline'));
    expect(service.isOffline()).toBe(true);
  });

  it('deve atualizar isOffline para false ao receber evento "online"', () => {
    window.dispatchEvent(new Event('offline'));
    window.dispatchEvent(new Event('online'));
    expect(service.isOffline()).toBe(false);
  });

  it('deve refletir sequência de eventos online/offline corretamente', () => {
    window.dispatchEvent(new Event('offline'));
    expect(service.isOffline()).toBe(true);

    window.dispatchEvent(new Event('online'));
    expect(service.isOffline()).toBe(false);

    window.dispatchEvent(new Event('offline'));
    expect(service.isOffline()).toBe(true);
  });

  it('deve remover listeners ao destruir o serviço', () => {
    // Simula destruição
    service.ngOnDestroy();

    // Após destruição, eventos não devem mais atualizar o signal
    const valorAntes = service.isOffline();
    window.dispatchEvent(new Event('offline'));
    window.dispatchEvent(new Event('online'));

    // O valor não muda porque os listeners foram removidos
    expect(service.isOffline()).toBe(valorAntes);
  });
});

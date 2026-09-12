import { TestBed } from '@angular/core/testing';
import { SessionService } from './session.service';
import { AccountInfo } from './models/account.model';

const mockAccount: AccountInfo = {
  id: 'acc-1',
  email: 'teste@satoshi.pet',
  address: 'bc1qtest123',
  petName: 'Satoshi',
};

describe('SessionService', () => {
  let service: SessionService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SessionService);
  });

  it('deve ser criado', () => {
    expect(service).toBeTruthy();
  });

  it('deve inicializar com account = null', () => {
    expect(service.account()).toBeNull();
  });

  it('deve inicializar isAuthenticated = false', () => {
    expect(service.isAuthenticated()).toBe(false);
  });

  it('deve definir account ao chamar setSession', () => {
    service.setSession(mockAccount);
    expect(service.account()).toEqual(mockAccount);
  });

  it('deve atualizar isAuthenticated para true após setSession', () => {
    service.setSession(mockAccount);
    expect(service.isAuthenticated()).toBe(true);
  });

  it('deve limpar account ao chamar clearSession', () => {
    service.setSession(mockAccount);
    service.clearSession();
    expect(service.account()).toBeNull();
  });

  it('deve retornar isAuthenticated = false após clearSession', () => {
    service.setSession(mockAccount);
    service.clearSession();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('deve sobrescrever conta anterior com novo setSession', () => {
    service.setSession(mockAccount);
    const novoAccount: AccountInfo = { id: 'acc-2', email: 'outro@satoshi.pet' };
    service.setSession(novoAccount);
    expect(service.account()?.email).toBe('outro@satoshi.pet');
  });

  it('account deve ser somente-leitura (não expõe o signal interno)', () => {
    // account é ReadonlySignal — não tem método .set
    expect(typeof (service.account as unknown as { set?: unknown }).set).toBe('undefined');
  });
});

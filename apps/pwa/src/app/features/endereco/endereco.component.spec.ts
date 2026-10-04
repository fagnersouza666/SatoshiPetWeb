import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';
import { ApiClientService } from '../../core/api-client.service';
import { AddressWebSocketService } from '../../core/address-websocket.service';
import { PublicAddressInfo } from '../../core/models/account.model';
import { EnderecoComponent } from './endereco.component';

const ADDRESS = 'bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh';

describe('EnderecoComponent', () => {
  let fixture: ComponentFixture<EnderecoComponent>;
  let apiSpy: { get: ReturnType<typeof vi.fn>; getFresh?: ReturnType<typeof vi.fn> };

  async function criarComponente(resposta: Observable<PublicAddressInfo>) {
    apiSpy = { get: vi.fn().mockReturnValue(resposta) };

    await TestBed.configureTestingModule({
      imports: [EnderecoComponent],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ address: ADDRESS }) } },
        },
        { provide: ApiClientService, useValue: apiSpy },
        {
          provide: AddressWebSocketService,
          useValue: { connect: vi.fn(), disconnect: vi.fn() },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EnderecoComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('deve exibir ovo textual e rótulo operacional sem estado emocional', async () => {
    const info: PublicAddressInfo = {
      address: ADDRESS,
      petName: 'Pixel',
      presentation: 'EGG',
      petState: undefined,
      reserveHours: '0.0000000000',
      awaitingReference: true,
      pendingMovesEgg: false,
      operationalLabel: 'Aguardando referência do plano',
    };
    await criarComponente(of(info));

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Ovo');
    expect(fixture.nativeElement.querySelector('img[src="/assets/egg.svg"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.pet-state')).toBeNull();

    const status = fixture.nativeElement.querySelector('[role="status"]');
    expect(status).not.toBeNull();
    expect(status?.textContent).toContain('Aguardando referência do plano');
  });

  it('deve exibir sprite canvas para criatura com atlas', async () => {
    const info: PublicAddressInfo = {
      address: ADDRESS,
      petName: 'Pixel',
      presentation: 'CREATURE',
      petState: 'ALIMENTADO',
      atlasUrl: '/api/v1/public/addresses/x/artwork/1/atlas.png',
      artworkVersion: '1',
    };
    await criarComponente(of(info));

    expect(fixture.nativeElement.querySelector('canvas')).not.toBeNull();
  });

  it('deve exibir estado emocional somente para criatura', async () => {
    const info: PublicAddressInfo = {
      address: ADDRESS,
      petName: 'Pixel',
      presentation: 'CREATURE',
      petState: 'ALIMENTADO',
      reserveHours: '24.0000000000',
      awaitingReference: false,
      pendingMovesEgg: false,
    };
    await criarComponente(of(info));

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).not.toMatch(/\bOvo\b/);
    expect(fixture.nativeElement.querySelector('.pet-state')?.textContent).toContain('Alimentado');
  });

  it('deve exibir estado de carregamento', async () => {
    const pending = new Subject<PublicAddressInfo>();
    apiSpy = { get: vi.fn().mockReturnValue(pending.asObservable()) };

    await TestBed.configureTestingModule({
      imports: [EnderecoComponent],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ address: ADDRESS }) } },
        },
        { provide: ApiClientService, useValue: apiSpy },
        {
          provide: AddressWebSocketService,
          useValue: { connect: vi.fn(), disconnect: vi.fn() },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EnderecoComponent);
    fixture.detectChanges();

    const loading = fixture.nativeElement.querySelector('[aria-busy="true"]');
    expect(loading).not.toBeNull();
    expect(loading?.textContent).toContain('Carregando');
    pending.complete();
  });

  it('deve exibir erro quando a API falha', async () => {
    await criarComponente(throwError(() => new Error('rede')));

    const alerta = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alerta).not.toBeNull();
    expect(alerta?.textContent).toContain('Não foi possível carregar os dados do endereço');
  });
  it('informa saldo desconhecido sem apresentar zero', async () => {
    await criarComponente(
      of({ address: ADDRESS, balanceKnown: false, confirmedSats: null } as PublicAddressInfo),
    );
    expect(fixture.nativeElement.textContent).toContain('Saldo ainda desconhecido');
    expect(fixture.nativeElement.textContent).not.toContain('0 sats');
  });

  it('avisa dado desatualizado preservando o último saldo conhecido', async () => {
    await criarComponente(
      of({
        address: ADDRESS,
        balanceKnown: true,
        balanceFresh: false,
        confirmedSats: 1500,
      } as PublicAddressInfo),
    );
    expect(fixture.nativeElement.textContent).toContain('1500 sats');
    expect(fixture.nativeElement.textContent).toContain('Saldo desatualizado');
  });

  it('eventos atualizam via HTTP sem cache e resposta antiga não regride o estado', async () => {
    const initial = new Subject<PublicAddressInfo>();
    await criarComponente(initial);
    const ws = TestBed.inject(AddressWebSocketService);
    const callback = vi.mocked(ws.connect).mock.calls[0][1];
    const first = new Subject<PublicAddressInfo>();
    const second = new Subject<PublicAddressInfo>();
    apiSpy.getFresh = vi.fn().mockReturnValueOnce(first).mockReturnValueOnce(second);
    callback({ type: 'EVENT', cursor: '1', data: { eventType: 'PET_STATE_CHANGED' } });
    callback({ type: 'EVENT', cursor: '2', data: { eventType: 'PET_STATE_CHANGED' } });
    second.next({
      address: ADDRESS,
      petName: 'Pixel',
      presentation: 'CREATURE',
      petState: 'FAMINTO',
    });
    await fixture.whenStable();
    initial.next({ address: ADDRESS, petName: 'Pixel', presentation: 'EGG' });
    first.next({
      address: ADDRESS,
      petName: 'Pixel',
      presentation: 'CREATURE',
      petState: 'ALIMENTADO',
    });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.pet-state')?.textContent).toContain('Faminto');
    expect(fixture.nativeElement.textContent).not.toContain('Alimentado');
    expect(apiSpy.getFresh).toHaveBeenCalledWith(`/v1/public/addresses/${ADDRESS}`);
  });
});

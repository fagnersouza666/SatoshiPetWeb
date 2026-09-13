import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';
import { ApiClientService } from '../../core/api-client.service';
import { PublicAddressInfo } from '../../core/models/account.model';
import { EnderecoComponent } from './endereco.component';

const ADDRESS = 'bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh';

describe('EnderecoComponent', () => {
  let fixture: ComponentFixture<EnderecoComponent>;
  let apiSpy: { get: ReturnType<typeof vi.fn> };

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
    expect(fixture.nativeElement.querySelector('img, svg')).toBeNull();
    expect(fixture.nativeElement.querySelector('.pet-state')).toBeNull();

    const status = fixture.nativeElement.querySelector('[role="status"]');
    expect(status).not.toBeNull();
    expect(status?.textContent).toContain('Aguardando referência do plano');
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
});

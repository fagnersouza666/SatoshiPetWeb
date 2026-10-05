import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ContaComponent } from './conta.component';
import { SessionService } from '../../core/session.service';
import { ApiClientService } from '../../core/api-client.service';
import { API_BASE_URL } from '../../core/api-config';

describe('Conta: emissão privada de código de recuperação', () => {
  let fixture: ComponentFixture<ContaComponent>;
  let session: SessionService;
  let http: HttpTestingController;
  const endpoint = '/api/v1/account/recovery/code';
  const code = 'CodigoPrivado_A-123';

  beforeEach(async () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ContaComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api' },
      ],
    });
    session = TestBed.inject(SessionService);
    session.setSession({ id: 'A', email: 'a@example.invalid' });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(ApiClientService).getFresh('/v1/account/me').subscribe();
    http.expectOne('/api/v1/account/me?ngsw-bypass=true').flush(
      {},
      {
        headers: { 'X-CSRF-Token': 'csrf-fixture' },
      },
    );
    fixture = TestBed.createComponent(ContaComponent);
    fixture.detectChanges();
    http
      .expectOne('/api/v1/account/pet?ngsw-bypass=true')
      .flush({ petName: 'Pet', presentation: 'EGG' });
    await fixture.whenStable();
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  function button(text: string): HTMLButtonElement {
    const found = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((item) => item.textContent?.includes(text));
    expect(found, `Ação disponível: ${text}`).toBeDefined();
    return found!;
  }

  async function generate(): Promise<void> {
    button('Gerar código de recuperação').click();
    http.expectOne(endpoint).flush({ code });
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('emite apenas sob comando, com sessão/CSRF, sem cache nem duplicar clique', async () => {
    http.expectNone(endpoint);
    const generateButton = button('Gerar código de recuperação');
    generateButton.click();
    generateButton.click();
    const request = http.expectOne(endpoint);
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.get('X-CSRF-Token')).toBe('csrf-fixture');
    expect(request.request.cache).toBe('no-store');
    request.flush({ code });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(code);
    expect(fixture.nativeElement.textContent).toMatch(/substitui.*anterior/i);
    expect(fixture.nativeElement.textContent).toMatch(/fora.*aplicativo/i);
  });

  it.each(['logout', 'troca'])('remove código mostrado após %s', async (operation) => {
    await generate();
    if (operation === 'logout') session.clearSession();
    else session.setSession({ id: 'B', email: 'b@example.invalid' });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain(code);
  });

  it('descarta emissão pendente da conta anterior', async () => {
    button('Gerar código de recuperação').click();
    const request = http.expectOne(endpoint);
    session.setSession({ id: 'B', email: 'b@example.invalid' });
    fixture.detectChanges();
    request.flush({ code });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain(code);
  });

  it('desabilita emissão offline e reabilita quando a conexão volta', async () => {
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);
    window.dispatchEvent(new Event('offline'));
    fixture.detectChanges();
    expect(button('Gerar código de recuperação').disabled).toBe(true);
    button('Gerar código de recuperação').click();
    http.expectNone(endpoint);
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(true);
    window.dispatchEvent(new Event('online'));
    fixture.detectChanges();
    expect(button('Gerar código de recuperação').disabled).toBe(false);
  });

  it('expõe erro de emissão sem apresentar sucesso ou código antigo', async () => {
    await generate();
    button('Gerar código de recuperação').click();
    http.expectOne(endpoint).flush({}, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain(code);
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'Não foi possível',
    );
  });

  it('copia e baixa somente o código exibido', async () => {
    await generate();
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal(
      'navigator',
      Object.assign(Object.create(navigator), { clipboard: { writeText } }),
    );
    const blobs: Blob[] = [];
    vi.spyOn(URL, 'createObjectURL').mockImplementation((blob) => {
      blobs.push(blob as Blob);
      return 'blob:recovery-fixture';
    });
    const revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    let download = '';
    let href = '';
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      download = this.download;
      href = this.href;
    });
    button('Copiar código').click();
    await fixture.whenStable();
    expect(writeText).toHaveBeenCalledWith(code);
    button('Baixar código').click();
    expect(download).toBe('satoshi-pet-recuperacao.txt');
    expect(href).toBe('blob:recovery-fixture');
    const contents = await new Promise<string>((resolve) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.readAsText(blobs[0]);
    });
    expect(contents).toContain(code);
    expect(revoke).toHaveBeenCalledWith('blob:recovery-fixture');
    vi.unstubAllGlobals();
  });
});

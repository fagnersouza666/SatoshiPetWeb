import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { EntrarComponent } from './entrar.component';
import { AuthService } from '../../../core/auth.service';

describe('EntrarComponent', () => {
  let fixture: ComponentFixture<EntrarComponent>;
  let component: EntrarComponent;
  let authSpy: { requestMagicLink: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    authSpy = { requestMagicLink: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [EntrarComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EntrarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deve ser criado', () => {
    expect(component).toBeTruthy();
  });

  it('deve renderizar o título "Entrar"', () => {
    const h1 = fixture.nativeElement.querySelector('h1');
    expect(h1?.textContent?.trim()).toBe('Entrar');
  });

  it('deve ter campo de e-mail com aria-required="true"', () => {
    const input = fixture.nativeElement.querySelector('input[type="email"]');
    expect(input?.getAttribute('aria-required')).toBe('true');
  });

  it('deve ter label associada ao campo de e-mail', () => {
    const label = fixture.nativeElement.querySelector('label[for="email"]');
    expect(label).not.toBeNull();
  });

  it('deve ter link para /cadastro', () => {
    const links = fixture.nativeElement.querySelectorAll('a[href]');
    const hrefs = Array.from(links).map((l) => (l as HTMLAnchorElement).getAttribute('href'));
    expect(hrefs.some((h) => h?.includes('cadastro'))).toBe(true);
  });

  it('deve ter link para /recuperar', () => {
    const links = fixture.nativeElement.querySelectorAll('a[href]');
    const hrefs = Array.from(links).map((l) => (l as HTMLAnchorElement).getAttribute('href'));
    expect(hrefs.some((h) => h?.includes('recuperar'))).toBe(true);
  });

  it('não deve chamar requestMagicLink com e-mail inválido', async () => {
    authSpy.requestMagicLink.mockReturnValue(of(undefined));
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type="email"]');
    input.value = 'nao-e-email';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    btn.click();
    fixture.detectChanges();

    expect(authSpy.requestMagicLink).not.toHaveBeenCalled();
  });

  it('deve chamar requestMagicLink com e-mail válido e exibir mensagem de sucesso', async () => {
    authSpy.requestMagicLink.mockReturnValue(of(undefined));

    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type="email"]');
    input.value = 'usuario@satoshi.pet';
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();

    // Preenche via formGroup para garantir validação correta
    component['form'].setValue({ email: 'usuario@satoshi.pet' });
    fixture.detectChanges();

    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    btn.click();

    await fixture.whenStable();
    fixture.detectChanges();

    expect(authSpy.requestMagicLink).toHaveBeenCalledWith('usuario@satoshi.pet');
    const successDiv = fixture.nativeElement.querySelector('[role="status"]');
    expect(successDiv).not.toBeNull();
  });

  it('deve exibir mensagem de erro quando requestMagicLink falha', async () => {
    authSpy.requestMagicLink.mockReturnValue(throwError(() => new Error('Erro de rede')));

    component['form'].setValue({ email: 'usuario@satoshi.pet' });
    fixture.detectChanges();

    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    btn.click();

    await fixture.whenStable();
    fixture.detectChanges();

    const errorDiv = fixture.nativeElement.querySelector('[role="alert"]');
    expect(errorDiv).not.toBeNull();
    expect(errorDiv?.textContent).toContain('Não foi possível enviar');
  });

  it('deve ter botão de submit com type="button" ou type="submit" acessível', () => {
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(btn).not.toBeNull();
  });
});

import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/auth.service';
import { RecuperarComponent } from './recuperar.component';

describe('RecuperarComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());
  function setup(token: string | null = null) {
    const auth = {
      requestRecoveryEmail: vi.fn().mockResolvedValue(undefined),
      recoverAccess: vi.fn().mockResolvedValue('NOVO-CODIGO-SECRETO'),
      openRecoveredAccount: vi.fn().mockResolvedValue(undefined),
    };
    TestBed.configureTestingModule({
      imports: [RecuperarComponent],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(token ? { token } : {}) } },
        },
        { provide: AuthService, useValue: auth },
      ],
    });
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(RecuperarComponent);
    fixture.detectChanges();
    const set = (name: string, value: string) => {
      const input = fixture.nativeElement.querySelector(`[formControlName="${name}"]`);
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    set('recoveryCode', ' recovery-fixture ');
    const submit = () =>
      fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    return { auth, fixture, set, submit };
  }

  it('envia confirmação para o novo e-mail antes de recuperar', async () => {
    const { auth, fixture, set, submit } = setup();
    set('email', 'novo@example.invalid');
    submit();
    submit();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(auth.requestRecoveryEmail).toHaveBeenCalledExactlyOnceWith(
      'recovery-fixture',
      'novo@example.invalid',
    );
    expect(auth.recoverAccess).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Verifique seu novo e-mail');
  });

  it('usa token verificado e exige guardar novo código antes de abrir conta', async () => {
    const { auth, fixture, submit } = setup('verified-token');
    submit();
    submit();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(auth.recoverAccess).toHaveBeenCalledExactlyOnceWith(
      'recovery-fixture',
      'verified-token',
    );
    expect(fixture.nativeElement.textContent).toContain('NOVO-CODIGO-SECRETO');
    expect(auth.openRecoveredAccount).not.toHaveBeenCalled();
    const button = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((item) => item.textContent?.includes('Guardei'))!;
    button.click();
    await fixture.whenStable();
    expect(auth.openRecoveredAccount).toHaveBeenCalledOnce();
  });

  it.each([401, 503])('falha %s permite nova tentativa e preserva etapa', async (status) => {
    const { auth, fixture, submit } = setup('verified-token');
    auth.recoverAccess.mockRejectedValue(new HttpErrorResponse({ status }));
    submit();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('NOVO-CODIGO-SECRETO');
    expect(fixture.nativeElement.querySelector('button').disabled).toBe(false);
    submit();
    await fixture.whenStable();
    expect(auth.recoverAccess).toHaveBeenCalledTimes(2);
  });
});

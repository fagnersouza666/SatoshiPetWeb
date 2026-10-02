import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from '../../../core/auth.service';
import { RecuperarComponent } from './recuperar.component';

describe('RecuperarComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());
  function setup(recoverAndNavigate: ReturnType<typeof vi.fn>) {
    TestBed.configureTestingModule({
      imports: [RecuperarComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { recoverAndNavigate } },
      ],
    });
    const fixture = TestBed.createComponent(RecuperarComponent);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input');
    input.value = ' recovery-fixture ';
    input.dispatchEvent(new Event('input'));
    const submit = () =>
      fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    return { fixture, submit };
  }

  it('recupera a sessão uma vez e não promete enviar um e-mail', async () => {
    let resolve!: () => void;
    const recover = vi.fn(
      () =>
        new Promise<void>((done) => {
          resolve = done;
        }),
    );
    const { fixture, submit } = setup(recover);
    submit();
    submit();
    expect(recover).toHaveBeenCalledExactlyOnceWith('recovery-fixture');
    resolve();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Acesso recuperado');
    expect(fixture.nativeElement.textContent).not.toContain('Verifique seu e-mail');
  });

  it('exibe erro e permite nova tentativa após código rejeitado', async () => {
    const recover = vi.fn().mockRejectedValue(new HttpErrorResponse({ status: 401 }));
    const { fixture, submit } = setup(recover);
    submit();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'Código inválido',
    );
    expect(fixture.nativeElement.querySelector('button').disabled).toBe(false);
    submit();
    await fixture.whenStable();
    expect(recover).toHaveBeenCalledTimes(2);
  });
  it('falha temporária não afirma que o código é inválido', async () => {
    const recover = vi.fn().mockRejectedValue(new HttpErrorResponse({ status: 503 }));
    const { fixture, submit } = setup(recover);
    submit();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'Tente novamente',
    );
    expect(fixture.nativeElement.textContent).not.toContain('Código inválido');
  });
});

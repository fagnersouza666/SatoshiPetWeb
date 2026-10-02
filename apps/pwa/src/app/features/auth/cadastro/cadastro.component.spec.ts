import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { AuthService } from '../../../core/auth.service';
import { SessionService } from '../../../core/session.service';
import { CadastroComponent } from './cadastro.component';

const account = {
  id: 'fixture',
  email: 'fixture@example.invalid',
  address: 'bc1q' + 'a'.repeat(38),
  petName: 'Satoshi',
};
describe('CadastroComponent: interrupção e repetição', () => {
  beforeEach(() => TestBed.resetTestingModule());
  function setup(pending = true) {
    const registered = new Subject<typeof account>();
    const register = vi.fn(() => registered);
    TestBed.configureTestingModule({
      imports: [CadastroComponent],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            register,
            pendingVerify: () => (pending ? { email: account.email, isNewUser: true } : null),
          },
        },
      ],
    });
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(CadastroComponent);
    fixture.detectChanges();
    for (const [name, value] of Object.entries({
      address: account.address,
      petName: account.petName,
    })) {
      const input = fixture.nativeElement.querySelector(`[formControlName="${name}"]`);
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    const submit = () =>
      fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    return { fixture, submit, register, registered, navigate };
  }

  it('interrupção sem token pendente volta ao login sem enviar cadastro', () => {
    const { navigate, register, submit } = setup(false);
    submit();
    expect(navigate).toHaveBeenCalledWith(['/entrar']);
    expect(register).not.toHaveBeenCalled();
  });

  it('envia somente um cadastro durante cliques repetidos', async () => {
    const { fixture, submit, register, registered, navigate } = setup();
    submit();
    submit();
    expect(register).toHaveBeenCalledExactlyOnceWith({
      address: account.address,
      petName: account.petName,
    });
    registered.next(account);
    await fixture.whenStable();
    expect(TestBed.inject(SessionService).account()).toEqual(account);
    expect(navigate).toHaveBeenCalledWith(['/conta']);
  });
});

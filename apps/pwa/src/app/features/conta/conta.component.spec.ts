import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ContaComponent } from './conta.component';

describe('ContaComponent: saída confirmada', () => {
  beforeEach(() => TestBed.resetTestingModule());
  function setup(logout: ReturnType<typeof vi.fn>) {
    TestBed.configureTestingModule({
      imports: [ContaComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: { logout } }],
    });
    const fixture = TestBed.createComponent(ContaComponent);
    fixture.detectChanges();
    return { fixture, click: () => fixture.nativeElement.querySelector('button').click() };
  }
  it('impede envio duplo enquanto a revogação está pendente', async () => {
    let resolve!: () => void;
    const logout = vi.fn(
      () =>
        new Promise<void>((done) => {
          resolve = done;
        }),
    );
    const { fixture, click } = setup(logout);
    click();
    click();
    expect(logout).toHaveBeenCalledOnce();
    resolve();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('button').disabled).toBe(false);
  });
  it('exibe falha de logout e permite tentar novamente', async () => {
    const logout = vi.fn().mockRejectedValue(new Error('403'));
    const { fixture, click } = setup(logout);
    click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'Erro ao sair',
    );
    click();
    await fixture.whenStable();
    expect(logout).toHaveBeenCalledTimes(2);
  });
});

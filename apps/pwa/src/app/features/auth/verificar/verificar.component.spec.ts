import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { AuthService } from '../../../core/auth.service';
import { VerificarComponent } from './verificar.component';

describe('VerificarComponent: retomada do acesso', () => {
  beforeEach(() => TestBed.resetTestingModule());
  function setup(status: number) {
    const verify = vi
      .fn()
      .mockRejectedValueOnce(new HttpErrorResponse({ status }))
      .mockResolvedValue({ isNewUser: false });
    TestBed.configureTestingModule({
      imports: [VerificarComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { verifyAndNavigate: verify } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({ token: 'fixture' }) } },
        },
      ],
    });
    const fixture = TestBed.createComponent(VerificarComponent);
    fixture.detectChanges();
    return { fixture, verify };
  }
  it('falha temporária permite retomar sem recarregar a página', async () => {
    const { fixture, verify } = setup(503);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Link inválido ou expirado');
    const retry = fixture.nativeElement.querySelector('button');
    expect(retry?.textContent).toContain('Tentar novamente');
    retry.click();
    retry.click();
    await fixture.whenStable();
    expect(verify).toHaveBeenCalledTimes(2);
    expect(verify).toHaveBeenLastCalledWith('fixture');
  });
  it('401 orienta solicitar novo link sem repetir o token rejeitado', async () => {
    const { fixture } = setup(401);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Link inválido ou expirado');
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });
});

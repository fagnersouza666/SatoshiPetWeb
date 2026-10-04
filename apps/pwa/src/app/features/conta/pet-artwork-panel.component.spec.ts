import { By } from '@angular/platform-browser';
import { PetSpriteComponent } from '../../shared/pet-sprite/pet-sprite.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PetArtworkPanelComponent } from './pet-artwork-panel.component';
import { PetAccountService } from '../../core/pet-account.service';
import { AccountPetSnapshot } from '../../core/models/pet-art.model';

describe('PetArtworkPanelComponent', () => {
  let fixture: ComponentFixture<PetArtworkPanelComponent>;
  let petAccountSpy: {
    loadSnapshot: ReturnType<typeof vi.fn>;
    approveArtwork: ReturnType<typeof vi.fn>;
    regenerateArtwork: ReturnType<typeof vi.fn>;
  };

  const snapshotAwaiting: AccountPetSnapshot = {
    petName: 'Pixel',
    presentation: 'EGG',
    operationalLabel: 'Preparando nascimento',
    artwork: {
      generationStatus: 'AWAITING_APPROVAL',
      canApprove: true,
      canRegenerate: true,
      previewUrls: { atlas: '/api/v1/account/pet/artwork/preview/atlas.png' },
      currentAttemptNo: 1,
    },
  };

  beforeEach(async () => {
    petAccountSpy = {
      loadSnapshot: vi.fn().mockResolvedValue(snapshotAwaiting),
      approveArtwork: vi.fn().mockResolvedValue(snapshotAwaiting.artwork),
      regenerateArtwork: vi.fn().mockResolvedValue({
        ...snapshotAwaiting.artwork,
        canRegenerate: false,
        currentAttemptNo: 2,
      }),
    };

    await TestBed.configureTestingModule({
      imports: [PetArtworkPanelComponent],
      providers: [{ provide: PetAccountService, useValue: petAccountSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(PetArtworkPanelComponent);
  });

  it('carrega snapshot no init sem regenerar', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(petAccountSpy.loadSnapshot).toHaveBeenCalledOnce();
    expect(petAccountSpy.regenerateArtwork).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Aprovar arte');
  });

  it('exibe botões somente quando criador pode agir', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('button')).not.toBeNull();
  });

  it('aprova arte sob demanda', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const buttons = fixture.nativeElement.querySelectorAll(
      'button',
    ) as NodeListOf<HTMLButtonElement>;
    const approveBtn = Array.from(buttons).find((btn) => btn.textContent?.includes('Aprovar'));
    expect(approveBtn).toBeDefined();
    approveBtn!.click();
    await fixture.whenStable();

    expect(petAccountSpy.approveArtwork).toHaveBeenCalledOnce();
    expect(petAccountSpy.loadSnapshot).toHaveBeenCalledTimes(2);
  });
  it('exibe a criatura da prévia privada enquanto apresentação pública continua EGG', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('canvas')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('img[src="/assets/egg.svg"]')).toBeNull();
    expect(snapshotAwaiting.presentation).toBe('EGG');
  });

  it('preserva ovo para arte aprovada quando o saldo leva ao retorno ao ovo', async () => {
    petAccountSpy.loadSnapshot.mockResolvedValue({
      ...snapshotAwaiting,
      atlasUrl: '/api/v1/public/addresses/x/artwork/1/atlas.png',
      artwork: { generationStatus: 'APPROVED' },
    });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('canvas')).toBeNull();
    expect(fixture.nativeElement.querySelector('img[src="/assets/egg.svg"]')).not.toBeNull();
  });

  it('carrega prévia privada com bypass inclusive em instalação com SW antigo', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const sprite = fixture.debugElement.query(By.directive(PetSpriteComponent))
      .componentInstance as PetSpriteComponent;
    expect(sprite.atlasUrl()).toBe(
      '/api/v1/account/pet/artwork/preview/atlas.png?ngsw-bypass=true',
    );
  });
});

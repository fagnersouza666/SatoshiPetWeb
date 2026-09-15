import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { PetAccountService } from '../../core/pet-account.service';
import { AccountPetSnapshot, ArtworkInfo } from '../../core/models/pet-art.model';
import { PetSpriteComponent } from '../../shared/pet-sprite/pet-sprite.component';

/**
 * UI do criador: preview, aprovar e regenerar 1× (ART-05, CA-037/038).
 * Carrega snapshot sem chamar regenerate automaticamente.
 */
@Component({
  selector: 'app-pet-artwork-panel',
  standalone: true,
  imports: [PetSpriteComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="artwork" aria-labelledby="artwork-titulo">
      <h2 id="artwork-titulo" class="artwork__title">Arte do pet</h2>

      @if (carregando()) {
        <p class="artwork__status" role="status" aria-live="polite">Carregando arte…</p>
      }

      @if (erro()) {
        <div class="alert alert--error" role="alert">{{ erro() }}</div>
      }

      @if (snapshot(); as snap) {
        <app-pet-sprite
          [presentation]="snap.presentation"
          [petState]="snap.petState"
          [atlasUrl]="previewAtlasUrl(snap)"
          [petName]="snap.petName"
        />

        @if (snap.operationalLabel) {
          <p class="artwork__status" role="status">{{ snap.operationalLabel }}</p>
        }

        @if (snap.artwork?.generationStatus === 'AWAITING_APPROVAL' && snap.artwork?.previewUrls) {
          <p class="artwork__hint">
            Revise a prévia antes de aprovar. Regeneração disponível uma vez.
          </p>
        }

        @if (snap.artwork?.generationStatus === 'RETRY_WAIT') {
          <p class="artwork__status" role="status">
            Falha técnica na geração. Tentaremos novamente em breve — seu sorteio permanece intacto.
          </p>
        }

        <div class="artwork__actions">
          @if (snap.artwork?.canApprove) {
            <button
              type="button"
              class="btn btn--primary"
              (click)="aprovar()"
              [disabled]="processando()"
            >
              Aprovar arte
            </button>
          }
          @if (snap.artwork?.canRegenerate) {
            <button
              type="button"
              class="btn btn--secondary"
              (click)="regenerar()"
              [disabled]="processando()"
            >
              Regenerar (1×)
            </button>
          }
        </div>
      }
    </section>
  `,
  styles: [
    `
      .artwork {
        margin-top: var(--space-6);
        padding-top: var(--space-4);
        border-top: 1px solid var(--color-border);
      }

      .artwork__title {
        font-size: 1.125rem;
        font-weight: 700;
        margin: 0 0 var(--space-4);
      }

      .artwork__status,
      .artwork__hint {
        font-size: 0.875rem;
        color: var(--color-text-muted);
        margin: var(--space-3) 0 0;
      }

      .artwork__actions {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
        margin-top: var(--space-4);
      }

      .alert {
        padding: var(--space-3) var(--space-4);
        border-radius: var(--radius-md);
        font-size: 0.9375rem;
        margin-bottom: var(--space-3);

        &--error {
          background: color-mix(in srgb, var(--color-danger) 10%, transparent);
          color: var(--color-danger);
          border: 1px solid color-mix(in srgb, var(--color-danger) 30%, transparent);
        }
      }

      .btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        padding: var(--space-3) var(--space-4);
        border-radius: var(--radius-md);
        font-size: 0.9375rem;
        font-weight: 600;
        cursor: pointer;
        min-height: 2.75rem;
        border: none;

        &--primary {
          background: var(--color-primary);
          color: #fff;
        }

        &--secondary {
          background: var(--color-surface);
          color: var(--color-primary);
          border: 1.5px solid var(--color-primary);
        }

        &:disabled {
          opacity: 0.6;
          cursor: not-allowed;
        }
      }
    `,
  ],
})
export class PetArtworkPanelComponent implements OnInit {
  private readonly petAccount = inject(PetAccountService);

  protected readonly carregando = signal(true);
  protected readonly processando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly snapshot = signal<AccountPetSnapshot | null>(null);

  ngOnInit(): void {
    void this.carregar();
  }

  protected previewAtlasUrl(snap: AccountPetSnapshot): string | undefined {
    if (snap.artwork?.previewUrls?.['atlas']) {
      return snap.artwork.previewUrls['atlas'];
    }
    return snap.atlasUrl;
  }

  protected async aprovar(): Promise<void> {
    await this.executar(() => this.petAccount.approveArtwork());
  }

  protected async regenerar(): Promise<void> {
    await this.executar(() => this.petAccount.regenerateArtwork());
  }

  private async carregar(): Promise<void> {
    this.carregando.set(true);
    this.erro.set(null);
    try {
      const snap = await this.petAccount.loadSnapshot();
      this.snapshot.set(snap);
    } catch {
      this.erro.set('Não foi possível carregar a arte do pet.');
    } finally {
      this.carregando.set(false);
    }
  }

  private async executar(action: () => Promise<ArtworkInfo>): Promise<void> {
    this.processando.set(true);
    this.erro.set(null);
    try {
      await action();
      await this.carregar();
    } catch {
      this.erro.set('Não foi possível concluir a operação. Tente novamente.');
    } finally {
      this.processando.set(false);
    }
  }
}

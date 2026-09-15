import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApiClientService } from '../../core/api-client.service';
import { AddressWebSocketService } from '../../core/address-websocket.service';
import { PublicAddressInfo } from '../../core/models/account.model';
import { PetEmotionalState } from '../../core/models/pet-art.model';
import { PetSpriteComponent } from '../../shared/pet-sprite/pet-sprite.component';
import { firstValueFrom } from 'rxjs';

/** Mapeamento de estado do pet para rótulo legível. */
const PET_STATE_LABELS: Record<NonNullable<PublicAddressInfo['petState']>, string> = {
  ALIMENTADO: 'Alimentado 🐾',
  PENSANDO: 'Pensando 💭',
  CHATEADO: 'Chateado 😟',
  FAMINTO: 'Faminto 😢',
  CRITICO: 'Crítico ⚠️',
  HIBERNANDO: 'Hibernando 💤',
};

/**
 * Página pública de visualização de endereço Bitcoin (BTC-01..05, ART-06/10).
 *
 * Consome GET /api/v1/public/addresses/{address} e exibe ovo ou sprite aprovado.
 * WebSocket recarrega atlas em PET_ARTWORK_READY.
 */
@Component({
  selector: 'app-endereco',
  standalone: true,
  imports: [PetSpriteComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page" aria-labelledby="endereco-titulo">
      <h1 id="endereco-titulo" class="page__title">Endereço Bitcoin</h1>

      @if (carregando()) {
        <div class="status" role="status" aria-live="polite" aria-busy="true">
          <span class="spinner" aria-hidden="true"></span>
          <span>Carregando dados do endereço…</span>
        </div>
      }

      @if (erro()) {
        <div class="alert alert--error" role="alert" aria-live="assertive" aria-atomic="true">
          {{ erro() }}
        </div>
      }

      @if (info(); as info) {
        <div class="address-card" aria-label="Informações do endereço">
          <div class="address-block">
            <span class="address-label" aria-hidden="true">Endereço</span>
            <p class="address-value" [attr.aria-label]="'Endereço Bitcoin: ' + info.address">
              {{ info.address }}
            </p>
          </div>

          @if (info.petName) {
            <div class="pet-block">
              <div class="pet-visual">
                <app-pet-sprite
                  [presentation]="info.presentation"
                  [petState]="info.petState"
                  [atlasUrl]="atlasUrl()"
                  [petName]="info.petName"
                />
                <span class="pet-name">{{ info.petName }}</span>
              </div>
              @if (info.presentation === 'EGG') {
                <span class="pet-egg" aria-label="Apresentação: ovo">Ovo</span>
              }
              @if (info.presentation === 'CREATURE' && info.petState) {
                <span
                  class="pet-state"
                  [class]="'pet-state pet-state--' + info.petState.toLowerCase()"
                  [attr.aria-label]="'Estado do pet: ' + petStateLabel(info.petState)"
                >
                  {{ petStateLabel(info.petState) }}
                </span>
              }
            </div>
          }

          @if (info.operationalLabel) {
            <p class="pet-operational" role="status">{{ info.operationalLabel }}</p>
          }
        </div>
      }

      @if (!carregando() && !info() && !erro()) {
        <p class="page__empty">Endereço não encontrado.</p>
      }
    </section>
  `,
  styles: [
    `
      .page {
        padding: var(--space-8) 0;
        max-width: 32rem;
      }

      .page__title {
        font-size: 1.5rem;
        font-weight: 700;
        margin: 0 0 var(--space-6);
        color: var(--color-text);
      }

      .page__empty {
        color: var(--color-text-muted);
      }

      .status {
        display: flex;
        align-items: center;
        gap: var(--space-3);
        color: var(--color-text-muted);
        font-size: 0.9375rem;
        padding: var(--space-4);
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
      }

      .spinner {
        display: inline-block;
        width: 1.25rem;
        height: 1.25rem;
        border: 2px solid var(--color-border);
        border-top-color: var(--color-primary);
        border-radius: 50%;
        flex-shrink: 0;
        animation: spin 0.7s linear infinite;
      }

      @keyframes spin {
        to {
          transform: rotate(360deg);
        }
      }

      .alert {
        padding: var(--space-3) var(--space-4);
        border-radius: var(--radius-md);
        font-size: 0.9375rem;

        &--error {
          background: color-mix(in srgb, var(--color-danger) 10%, transparent);
          color: var(--color-danger);
          border: 1px solid color-mix(in srgb, var(--color-danger) 30%, transparent);
        }
      }

      .address-card {
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-lg);
        padding: var(--space-4) var(--space-6);
      }

      .address-block {
        display: flex;
        flex-direction: column;
        gap: var(--space-1);
        margin-bottom: var(--space-4);
        padding-bottom: var(--space-4);
        border-bottom: 1px solid var(--color-border);
      }

      .address-label {
        font-size: 0.75rem;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: var(--color-text-muted);
      }

      .address-value {
        font-family: var(--font-mono);
        font-size: 0.8125rem;
        background: color-mix(in srgb, var(--color-border) 30%, transparent);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-sm);
        padding: var(--space-2) var(--space-3);
        word-break: break-all;
        margin: 0;
        color: var(--color-text);
      }

      .pet-block {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        flex-wrap: wrap;
        gap: var(--space-3);
      }

      .pet-visual {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--space-2);
      }

      .pet-name {
        font-size: 1.125rem;
        font-weight: 700;
        color: var(--color-text);
      }

      .pet-egg {
        font-size: 0.875rem;
        font-weight: 500;
        padding: var(--space-1) var(--space-3);
        border-radius: 9999px;
        background: color-mix(in srgb, var(--color-text-muted) 15%, transparent);
        color: var(--color-text-muted);
      }

      .pet-operational {
        margin: var(--space-3) 0 0;
        font-size: 0.875rem;
        color: var(--color-text-muted);
      }

      .pet-state {
        font-size: 0.875rem;
        font-weight: 500;
        padding: var(--space-1) var(--space-3);
        border-radius: 9999px;
        background: color-mix(in srgb, var(--color-primary) 15%, transparent);
        color: var(--color-primary-dark);

        &--hibernando {
          background: color-mix(in srgb, var(--color-text-muted) 15%, transparent);
          color: var(--color-text-muted);
        }

        &--critico,
        &--faminto {
          background: color-mix(in srgb, var(--color-danger) 15%, transparent);
          color: var(--color-danger);
        }
      }
    `,
  ],
})
export class EnderecoComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ApiClientService);
  private readonly addressWs = inject(AddressWebSocketService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly info = signal<PublicAddressInfo | null>(null);
  protected readonly atlasUrl = signal<string | undefined>(undefined);

  ngOnInit(): void {
    const address = this.route.snapshot.paramMap.get('address');
    if (address) {
      void this.carregarEndereco(address);
      this.addressWs.connect(address, () => {
        void this.carregarEndereco(address);
      });
      this.destroyRef.onDestroy(() => this.addressWs.disconnect());
    }
  }

  private async carregarEndereco(address: string): Promise<void> {
    this.carregando.set(true);
    this.erro.set(null);

    try {
      const data = await firstValueFrom(
        this.api.get<PublicAddressInfo>(`/v1/public/addresses/${address}`),
      );
      this.info.set(data);
      this.atlasUrl.set(
        data.atlasUrl ? `${data.atlasUrl}?v=${data.artworkVersion ?? '0'}` : undefined,
      );
    } catch {
      this.erro.set('Não foi possível carregar os dados do endereço. Tente novamente.');
    } finally {
      this.carregando.set(false);
    }
  }

  protected petStateLabel(state: PetEmotionalState): string {
    return PET_STATE_LABELS[state] ?? state;
  }
}

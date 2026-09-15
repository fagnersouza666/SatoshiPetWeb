import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SessionService } from '../../core/session.service';
import { AuthService } from '../../core/auth.service';
import { PetArtworkPanelComponent } from './pet-artwork-panel.component';

/**
 * Página da conta autenticada (CONTA-05).
 *
 * Exibe informações da conta (e-mail, endereço Bitcoin, nome do pet)
 * e disponibiliza as ações de logout e acesso à visualização do endereço.
 *
 * Protegida pelo authGuard — não exibida sem sessão válida.
 * Acessibilidade: WCAG 2.2 AA — regiões semânticas, foco gerenciado no logout.
 */
@Component({
  selector: 'app-conta',
  standalone: true,
  imports: [RouterLink, PetArtworkPanelComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page" aria-labelledby="conta-titulo">
      <h1 id="conta-titulo" class="page__title">Minha conta</h1>

      @if (conta()) {
        <div class="card" aria-label="Informações da conta">
          <dl class="info-list">
            <div class="info-item">
              <dt class="info-item__label">E-mail</dt>
              <dd class="info-item__value">{{ conta()!.email }}</dd>
            </div>

            @if (conta()!.petName) {
              <div class="info-item">
                <dt class="info-item__label">Nome do pet</dt>
                <dd class="info-item__value">{{ conta()!.petName }}</dd>
              </div>
            }

            @if (conta()!.address) {
              <div class="info-item">
                <dt class="info-item__label">Endereço Bitcoin</dt>
                <dd class="info-item__value info-item__value--mono">
                  {{ conta()!.address }}
                </dd>
              </div>
            }
          </dl>

          @if (conta()!.address) {
            <a
              [routerLink]="['/endereco', conta()!.address]"
              class="btn btn--secondary"
              aria-label="Ver visualização pública do endereço Bitcoin"
            >
              Ver endereço público
            </a>
          }
        </div>

        <app-pet-artwork-panel />
      }

      @if (erroLogout()) {
        <div class="alert alert--error" role="alert" aria-live="assertive">
          {{ erroLogout() }}
        </div>
      }

      <div class="actions">
        <button
          type="button"
          class="btn btn--danger"
          (click)="sair()"
          [disabled]="saindo()"
          [attr.aria-busy]="saindo()"
          aria-label="Sair da conta"
        >
          @if (saindo()) {
            <span class="btn__spinner" aria-hidden="true"></span>
            Saindo…
          } @else {
            Sair da conta
          }
        </button>
      </div>
    </section>
  `,
  styles: [
    `
      .page {
        padding: var(--space-8) 0;
        max-width: 28rem;
      }

      .page__title {
        font-size: 1.5rem;
        font-weight: 700;
        margin: 0 0 var(--space-6);
        color: var(--color-text);
      }

      .card {
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-lg);
        padding: var(--space-4) var(--space-6);
        margin-bottom: var(--space-6);
      }

      .info-list {
        margin: 0 0 var(--space-4);
        padding: 0;
      }

      .info-item {
        display: flex;
        flex-direction: column;
        gap: var(--space-1);
        padding: var(--space-3) 0;
        border-bottom: 1px solid var(--color-border);

        &:last-child {
          border-bottom: none;
        }

        &__label {
          font-size: 0.75rem;
          font-weight: 600;
          text-transform: uppercase;
          letter-spacing: 0.05em;
          color: var(--color-text-muted);
        }

        &__value {
          font-size: 0.9375rem;
          color: var(--color-text);
          margin: 0;
          word-break: break-all;

          &--mono {
            font-family: var(--font-mono);
            font-size: 0.8125rem;
          }
        }
      }

      .alert {
        padding: var(--space-3) var(--space-4);
        border-radius: var(--radius-md);
        font-size: 0.9375rem;
        margin-bottom: var(--space-4);

        &--error {
          background: color-mix(in srgb, var(--color-danger) 10%, transparent);
          color: var(--color-danger);
          border: 1px solid color-mix(in srgb, var(--color-danger) 30%, transparent);
        }
      }

      .actions {
        display: flex;
        flex-direction: column;
        gap: var(--space-3);
      }

      .btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: var(--space-2);
        padding: var(--space-3) var(--space-4);
        border: none;
        border-radius: var(--radius-md);
        font-size: 0.9375rem;
        font-weight: 600;
        cursor: pointer;
        text-decoration: none;
        transition:
          background 0.15s ease,
          opacity 0.15s ease;
        min-height: 2.75rem;

        &--secondary {
          background: var(--color-surface);
          color: var(--color-primary);
          border: 1.5px solid var(--color-primary);
          width: 100%;
          &:hover:not(:disabled) {
            background: color-mix(in srgb, var(--color-primary) 8%, transparent);
          }
          &:focus-visible {
            outline: 2px solid var(--color-primary);
            outline-offset: 3px;
          }
        }

        &--danger {
          background: var(--color-danger);
          color: #fff;
          width: 100%;
          &:hover:not(:disabled) {
            background: color-mix(in srgb, var(--color-danger) 85%, #000);
          }
          &:focus-visible {
            outline: 2px solid var(--color-danger);
            outline-offset: 3px;
          }
        }

        &:disabled {
          opacity: 0.6;
          cursor: not-allowed;
        }

        &__spinner {
          display: inline-block;
          width: 1rem;
          height: 1rem;
          border: 2px solid currentColor;
          border-top-color: transparent;
          border-radius: 50%;
          animation: spin 0.7s linear infinite;
        }
      }

      @keyframes spin {
        to {
          transform: rotate(360deg);
        }
      }
    `,
  ],
})
export class ContaComponent {
  private readonly session = inject(SessionService);
  private readonly auth = inject(AuthService);

  protected readonly conta = this.session.account;
  protected readonly saindo = signal(false);
  protected readonly erroLogout = signal<string | null>(null);

  protected async sair(): Promise<void> {
    this.saindo.set(true);
    this.erroLogout.set(null);

    try {
      await this.auth.logout();
    } catch {
      this.erroLogout.set('Erro ao sair. Tente novamente.');
    } finally {
      this.saindo.set(false);
    }
  }
}

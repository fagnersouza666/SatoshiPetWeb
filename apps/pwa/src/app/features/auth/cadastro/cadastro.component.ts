import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/auth.service';
import { SessionService } from '../../../core/session.service';

/**
 * Página de cadastro de conta (CONTA-03).
 *
 * Exibida somente após verificação bem-sucedida do magic-link para novos usuários.
 * Coleta endereço Bitcoin e nome do pet para completar o registro.
 *
 * Validações:
 * - Endereço: formato básico (letras e dígitos, 26–62 caracteres)
 * - Nome do pet: 1–30 caracteres
 *
 * Acessibilidade: WCAG 2.2 AA — labels associados, aria-describedby para erros.
 */
@Component({
  selector: 'app-cadastro',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page" aria-labelledby="cadastro-titulo">
      <h1 id="cadastro-titulo" class="page__title">Criar conta</h1>

      @if (emailPendente()) {
        <p class="page__email">
          Registro para <strong>{{ emailPendente() }}</strong>
        </p>
      }

      @if (erro()) {
        <div class="alert alert--error" role="alert" aria-live="assertive" aria-atomic="true">
          {{ erro() }}
        </div>
      }

      <form
        [formGroup]="form"
        (ngSubmit)="registrar()"
        novalidate
        aria-label="Formulário de cadastro"
      >
        <div class="form-group">
          <label class="form-label" for="address">
            Endereço Bitcoin
            <span class="form-label__hint">(mainnet)</span>
          </label>
          <input
            id="address"
            type="text"
            formControlName="address"
            class="form-input form-input--mono"
            [class.form-input--error]="enderecoInvalido"
            autocomplete="off"
            autocorrect="off"
            autocapitalize="off"
            spellcheck="false"
            aria-required="true"
            [attr.aria-invalid]="enderecoInvalido ? 'true' : null"
            aria-describedby="address-hint address-erro"
            placeholder="bc1q…"
          />
          <span id="address-hint" class="field-hint">
            Endereço que receberá os satoshis monitorados pelo pet.
          </span>
          @if (enderecoInvalido) {
            <span id="address-erro" class="field-error" aria-live="polite">
              Informe um endereço Bitcoin válido (26–62 caracteres alfanuméricos).
            </span>
          }
        </div>

        <div class="form-group">
          <label class="form-label" for="petName">Nome do pet</label>
          <input
            id="petName"
            type="text"
            formControlName="petName"
            class="form-input"
            [class.form-input--error]="nomeInvalido"
            autocomplete="off"
            aria-required="true"
            [attr.aria-invalid]="nomeInvalido ? 'true' : null"
            aria-describedby="petName-erro"
            placeholder="Ex.: Satoshi"
            maxlength="30"
          />
          @if (nomeInvalido) {
            <span id="petName-erro" class="field-error" aria-live="polite">
              Informe um nome com até 30 caracteres.
            </span>
          }
        </div>

        <button
          type="submit"
          class="btn btn--primary"
          [disabled]="carregando()"
          [attr.aria-busy]="carregando()"
        >
          @if (carregando()) {
            <span class="btn__spinner" aria-hidden="true"></span>
            Criando conta…
          } @else {
            Criar conta
          }
        </button>
      </form>

      <nav class="page__links" aria-label="Opções alternativas">
        <a routerLink="/entrar">Já tenho conta</a>
      </nav>
    </section>
  `,
  styles: [
    `
      .page {
        padding: var(--space-8) 0;
        max-width: 26rem;
      }

      .page__title {
        font-size: 1.5rem;
        font-weight: 700;
        margin: 0 0 var(--space-2);
        color: var(--color-text);
      }

      .page__email {
        font-size: 0.9375rem;
        color: var(--color-text-muted);
        margin: 0 0 var(--space-6);
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

      .form-group {
        display: flex;
        flex-direction: column;
        gap: var(--space-1);
        margin-bottom: var(--space-4);
      }

      .form-label {
        font-size: 0.875rem;
        font-weight: 600;
        color: var(--color-text);

        &__hint {
          font-weight: 400;
          color: var(--color-text-muted);
          margin-left: var(--space-1);
        }
      }

      .form-input {
        width: 100%;
        padding: var(--space-3) var(--space-4);
        border: 1.5px solid var(--color-border);
        border-radius: var(--radius-md);
        font-size: 1rem;
        background: var(--color-surface);
        color: var(--color-text);
        box-sizing: border-box;
        transition: border-color 0.15s ease;

        &:focus {
          outline: none;
          border-color: var(--color-primary);
          box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-primary) 20%, transparent);
        }

        &--mono {
          font-family: var(--font-mono);
          font-size: 0.875rem;
        }
        &--error {
          border-color: var(--color-danger);
        }
        &::placeholder {
          color: var(--color-text-muted);
        }
      }

      .field-hint {
        font-size: 0.8125rem;
        color: var(--color-text-muted);
      }

      .field-error {
        font-size: 0.8125rem;
        color: var(--color-danger);
      }

      .btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: var(--space-2);
        width: 100%;
        padding: var(--space-3) var(--space-4);
        border: none;
        border-radius: var(--radius-md);
        font-size: 1rem;
        font-weight: 600;
        cursor: pointer;
        transition: background 0.15s ease;
        min-height: 2.75rem;

        &--primary {
          background: var(--color-primary);
          color: #0f172a;
          &:hover:not(:disabled) {
            background: var(--color-primary-dark);
          }
          &:focus-visible {
            outline: 2px solid var(--color-primary);
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

      .page__links {
        display: flex;
        gap: var(--space-2);
        font-size: 0.875rem;
        margin-top: var(--space-4);

        a {
          color: var(--color-primary);
          text-decoration: none;
          &:hover {
            text-decoration: underline;
          }
          &:focus-visible {
            outline: 2px solid var(--color-primary);
            outline-offset: 2px;
            border-radius: 2px;
          }
        }
      }
    `,
  ],
})
export class CadastroComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  /**
   * Padrão de validação de endereço Bitcoin (validação de primeiro nível — o servidor valida definitivamente).
   * - Legacy P2PKH/P2SH: começa com `1` ou `3`, 25–34 chars totais (Base58Check).
   * - Native SegWit bech32 (P2WPKH/P2WSH/Taproot): começa com `bc1`, 42–62 chars totais.
   */
  private readonly BITCOIN_ADDRESS_PATTERN =
    /^[13][a-km-zA-HJ-NP-Z1-9]{24,33}$|^bc1[a-z0-9]{39,59}$/;

  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly emailPendente = signal<string | null>(null);

  protected readonly form = this.fb.group({
    address: ['', [Validators.required, Validators.pattern(this.BITCOIN_ADDRESS_PATTERN)]],
    petName: ['', [Validators.required, Validators.maxLength(30)]],
  });

  protected get enderecoInvalido(): boolean {
    const ctrl = this.form.get('address');
    return !!(ctrl?.invalid && ctrl.touched);
  }

  protected get nomeInvalido(): boolean {
    const ctrl = this.form.get('petName');
    return !!(ctrl?.invalid && ctrl.touched);
  }

  ngOnInit(): void {
    const pendente = this.auth.pendingVerify();
    if (pendente) {
      this.emailPendente.set(pendente.email);
    }
  }

  protected async registrar(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;

    const address = this.form.value.address!.trim();
    const petName = this.form.value.petName!.trim();

    this.carregando.set(true);
    this.erro.set(null);

    try {
      const account = await firstValueFrom(this.auth.register({ address, petName }));
      this.session.setSession(account);
      await this.router.navigate(['/conta']);
    } catch {
      this.erro.set('Não foi possível criar a conta. Verifique os dados e tente novamente.');
    } finally {
      this.carregando.set(false);
    }
  }
}

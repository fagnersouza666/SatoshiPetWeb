import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiClientService } from '../../../core/api-client.service';
import { firstValueFrom } from 'rxjs';

/**
 * Página de recuperação de acesso por código de backup (CONTA-04).
 *
 * O usuário informa o código de recuperação gerado no cadastro;
 * o servidor valida e envia um magic-link para o e-mail associado.
 *
 * Acessibilidade: WCAG 2.2 AA — labels associados, feedback com aria-live.
 */
@Component({
  selector: 'app-recuperar',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page" aria-labelledby="recuperar-titulo">
      <h1 id="recuperar-titulo" class="page__title">Recuperar acesso</h1>
      <p class="page__sub">
        Informe o código de recuperação recebido no cadastro para obter um novo link de acesso.
      </p>

      @if (sucesso()) {
        <div class="alert alert--success" role="status" aria-live="polite" aria-atomic="true">
          <svg
            class="alert__icon"
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <path fill="currentColor" d="M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z" />
          </svg>
          <span>Código validado. Verifique seu e-mail para o link de acesso.</span>
        </div>
      }

      @if (erro()) {
        <div class="alert alert--error" role="alert" aria-live="assertive" aria-atomic="true">
          {{ erro() }}
        </div>
      }

      @if (!sucesso()) {
        <form
          [formGroup]="form"
          (ngSubmit)="recuperar()"
          novalidate
          aria-label="Formulário de recuperação de acesso"
        >
          <div class="form-group">
            <label class="form-label" for="recoveryCode">Código de recuperação</label>
            <input
              id="recoveryCode"
              type="text"
              formControlName="recoveryCode"
              class="form-input form-input--mono"
              [class.form-input--error]="codigoInvalido"
              autocomplete="off"
              autocorrect="off"
              autocapitalize="characters"
              spellcheck="false"
              aria-required="true"
              [attr.aria-invalid]="codigoInvalido ? 'true' : null"
              aria-describedby="recoveryCode-erro"
              placeholder="XXXX-XXXX-XXXX"
            />
            @if (codigoInvalido) {
              <span id="recoveryCode-erro" class="field-error" aria-live="polite">
                Informe o código de recuperação.
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
              Verificando…
            } @else {
              Recuperar acesso
            }
          </button>
        </form>
      }

      <nav class="page__links" aria-label="Outras opções">
        <a routerLink="/entrar">Voltar ao login</a>
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

      .page__sub {
        color: var(--color-text-muted);
        margin: 0 0 var(--space-6);
        font-size: 0.9375rem;
        line-height: 1.5;
      }

      .alert {
        display: flex;
        align-items: flex-start;
        gap: var(--space-2);
        padding: var(--space-3) var(--space-4);
        border-radius: var(--radius-md);
        font-size: 0.9375rem;
        margin-bottom: var(--space-4);

        &--success {
          background: color-mix(in srgb, var(--color-success) 12%, transparent);
          color: var(--color-success);
          border: 1px solid color-mix(in srgb, var(--color-success) 30%, transparent);
        }

        &--error {
          background: color-mix(in srgb, var(--color-danger) 10%, transparent);
          color: var(--color-danger);
          border: 1px solid color-mix(in srgb, var(--color-danger) 30%, transparent);
        }

        &__icon {
          flex-shrink: 0;
          margin-top: 0.1rem;
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
        }
        &--error {
          border-color: var(--color-danger);
        }
        &::placeholder {
          color: var(--color-text-muted);
        }
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
export class RecuperarComponent {
  private readonly api = inject(ApiClientService);
  private readonly fb = inject(FormBuilder);

  protected readonly carregando = signal(false);
  protected readonly sucesso = signal(false);
  protected readonly erro = signal<string | null>(null);

  protected readonly form = this.fb.group({
    recoveryCode: ['', [Validators.required, Validators.minLength(1)]],
  });

  protected get codigoInvalido(): boolean {
    const ctrl = this.form.get('recoveryCode');
    return !!(ctrl?.invalid && ctrl.touched);
  }

  protected async recuperar(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;

    const recoveryCode = this.form.value.recoveryCode!.trim();
    this.carregando.set(true);
    this.erro.set(null);

    try {
      await firstValueFrom(this.api.post<void>('/v1/auth/recover', { recoveryCode }));
      this.sucesso.set(true);
    } catch {
      this.erro.set('Código inválido ou já utilizado. Verifique o código e tente novamente.');
    } finally {
      this.carregando.set(false);
    }
  }
}

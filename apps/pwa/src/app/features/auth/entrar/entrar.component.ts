import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth.service';
import { firstValueFrom } from 'rxjs';

/**
 * Página de autenticação por magic-link (CONTA-01).
 *
 * Fluxo: usuário informa e-mail → servidor envia link →
 * mensagem de confirmação exibida.
 *
 * Acessibilidade: WCAG 2.2 AA — labels associados, aria-live para feedback,
 * foco gerenciado em estado de sucesso/erro.
 */
@Component({
  selector: 'app-entrar',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page" aria-labelledby="entrar-titulo">
      <h1 id="entrar-titulo" class="page__title">Entrar</h1>
      <p class="page__sub">Informe seu e-mail para receber um link de acesso.</p>

      @if (sucesso()) {
        <div class="alert alert--success" role="status" aria-live="polite" aria-atomic="true">
          <svg class="alert__icon" xmlns="http://www.w3.org/2000/svg" width="20" height="20"
               viewBox="0 0 24 24" aria-hidden="true">
            <path fill="currentColor"
              d="M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>
          </svg>
          <span>Link enviado para <strong>{{ emailEnviado() }}</strong>. Verifique sua caixa de entrada.</span>
        </div>
      }

      @if (erro()) {
        <div class="alert alert--error" role="alert" aria-live="assertive" aria-atomic="true">
          {{ erro() }}
        </div>
      }

      @if (!sucesso()) {
        <form [formGroup]="form" (ngSubmit)="enviar()" novalidate aria-label="Formulário de acesso">
          <div class="form-group">
            <label class="form-label" for="email">E-mail</label>
            <input
              id="email"
              type="email"
              formControlName="email"
              class="form-input"
              [class.form-input--error]="emailInvalido"
              autocomplete="email"
              inputmode="email"
              aria-required="true"
              [attr.aria-invalid]="emailInvalido ? 'true' : null"
              aria-describedby="email-erro"
              placeholder="seuemail@exemplo.com"
            />
            @if (emailInvalido) {
              <span id="email-erro" class="field-error" aria-live="polite">
                Informe um e-mail válido.
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
              Enviando…
            } @else {
              Enviar link de acesso
            }
          </button>
        </form>
      }

      <nav class="page__links" aria-label="Outras opções de acesso">
        <a routerLink="/cadastro">Criar conta</a>
        <span aria-hidden="true">·</span>
        <a routerLink="/recuperar">Recuperar acesso</a>
      </nav>
    </section>
  `,
  styles: [`
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
      transition: background 0.15s ease, opacity 0.15s ease;
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
      to { transform: rotate(360deg); }
    }

    .page__links {
      display: flex;
      gap: var(--space-2);
      font-size: 0.875rem;
      margin-top: var(--space-4);
      color: var(--color-text-muted);

      a {
        color: var(--color-primary);
        text-decoration: none;

        &:hover { text-decoration: underline; }
        &:focus-visible {
          outline: 2px solid var(--color-primary);
          outline-offset: 2px;
          border-radius: 2px;
        }
      }
    }
  `],
})
export class EntrarComponent {
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly carregando = signal(false);
  protected readonly sucesso = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly emailEnviado = signal('');

  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
  });

  protected get emailInvalido(): boolean {
    const ctrl = this.form.get('email');
    return !!(ctrl?.invalid && ctrl.touched);
  }

  protected async enviar(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;

    const email = this.form.value.email!.trim();
    this.carregando.set(true);
    this.erro.set(null);

    try {
      await firstValueFrom(this.auth.requestMagicLink(email));
      this.emailEnviado.set(email);
      this.sucesso.set(true);
    } catch {
      this.erro.set('Não foi possível enviar o link. Tente novamente em instantes.');
    } finally {
      this.carregando.set(false);
    }
  }
}

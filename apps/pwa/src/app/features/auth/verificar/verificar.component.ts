import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth.service';

/**
 * Página de verificação do magic-link (CONTA-02).
 *
 * Lê o token do query param `?token=…`, chama a API de verificação e:
 * - Usuário existente → redireciona para /conta
 * - Novo usuário → redireciona para /cadastro
 *
 * Acessibilidade: WCAG 2.2 AA — status de carregamento com aria-live,
 * erro apresentado com role="alert".
 */
@Component({
  selector: 'app-verificar',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page" aria-labelledby="verificar-titulo">
      <h1 id="verificar-titulo" class="page__title">Verificando acesso</h1>

      @if (carregando()) {
        <div class="status" role="status" aria-live="polite" aria-busy="true">
          <span class="spinner" aria-hidden="true"></span>
          <span>Validando seu link de acesso…</span>
        </div>
      }

      @if (erro()) {
        <div class="alert alert--error" role="alert" aria-live="assertive" aria-atomic="true">
          <strong>Link inválido ou expirado.</strong>
          <p>{{ erro() }}</p>
        </div>
        <nav class="page__links" aria-label="Opções de recuperação">
          <a routerLink="/entrar">Solicitar novo link</a>
          <span aria-hidden="true">·</span>
          <a routerLink="/recuperar">Recuperar acesso</a>
        </nav>
      }

      @if (semToken()) {
        <div class="alert alert--error" role="alert">
          Link incompleto. <a routerLink="/entrar">Volte ao login</a> para solicitar um novo.
        </div>
      }
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
        margin: 0 0 var(--space-6);
        color: var(--color-text);
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
        padding: var(--space-4);
        border-radius: var(--radius-md);
        font-size: 0.9375rem;
        margin-bottom: var(--space-4);

        strong {
          display: block;
          margin-bottom: var(--space-1);
        }
        p {
          margin: 0;
        }

        a {
          color: var(--color-primary);
          &:hover {
            text-decoration: underline;
          }
        }

        &--error {
          background: color-mix(in srgb, var(--color-danger) 10%, transparent);
          color: var(--color-danger);
          border: 1px solid color-mix(in srgb, var(--color-danger) 30%, transparent);
        }
      }

      .page__links {
        display: flex;
        gap: var(--space-2);
        font-size: 0.875rem;
        color: var(--color-text-muted);

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
export class VerificarComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);

  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly semToken = signal(false);

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');

    if (!token) {
      this.semToken.set(true);
      return;
    }

    this.verificar(token);
  }

  private async verificar(token: string): Promise<void> {
    this.carregando.set(true);
    this.erro.set(null);

    try {
      await this.auth.verifyAndNavigate(token);
    } catch {
      this.erro.set('O link pode ter expirado ou já ter sido utilizado. Solicite um novo.');
    } finally {
      this.carregando.set(false);
    }
  }
}

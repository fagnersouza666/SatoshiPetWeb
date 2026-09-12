import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Página de cadastro de conta (placeholder — épico CONTA).
 * Implementada na história CONTA-03.
 */
@Component({
  selector: 'app-cadastro',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page" aria-labelledby="cadastro-titulo">
      <h1 id="cadastro-titulo">Criar conta</h1>
      <p>Formulário de cadastro — em breve.</p>
      <a routerLink="/entrar">Já tenho conta</a>
    </section>
  `,
  styles: [`
    .page { padding: 2rem 0; }
    h1 { font-size: 1.5rem; margin: 0 0 0.5rem; }
    p { color: var(--color-text-muted); margin: 0 0 1rem; }
    a { color: var(--color-primary); font-size: 0.875rem; }
  `],
})
export class CadastroComponent {}

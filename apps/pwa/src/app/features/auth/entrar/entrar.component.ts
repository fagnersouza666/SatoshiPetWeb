import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Página de autenticação (placeholder — épico CONTA).
 * Exibe formulário de entrada; lógica implementada na história CONTA-01.
 */
@Component({
  selector: 'app-entrar',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page" aria-labelledby="entrar-titulo">
      <h1 id="entrar-titulo">Entrar</h1>
      <p>Formulário de acesso — em breve.</p>
      <nav aria-label="Outras opções">
        <a routerLink="/cadastro">Criar conta</a> ·
        <a routerLink="/recuperar">Recuperar acesso</a>
      </nav>
    </section>
  `,
  styles: [`
    .page { padding: 2rem 0; }
    h1 { font-size: 1.5rem; margin: 0 0 0.5rem; }
    p { color: var(--color-text-muted); margin: 0 0 1rem; }
    nav { display: flex; gap: 0.5rem; font-size: 0.875rem; }
    a { color: var(--color-primary); }
  `],
})
export class EntrarComponent {}

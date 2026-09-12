import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Página de recuperação de acesso (placeholder — épico CONTA).
 * Implementada na história CONTA-04.
 */
@Component({
  selector: 'app-recuperar',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page" aria-labelledby="recuperar-titulo">
      <h1 id="recuperar-titulo">Recuperar acesso</h1>
      <p>Fluxo de recuperação — em breve.</p>
      <a routerLink="/entrar">Voltar ao login</a>
    </section>
  `,
  styles: [`
    .page { padding: 2rem 0; }
    h1 { font-size: 1.5rem; margin: 0 0 0.5rem; }
    p { color: var(--color-text-muted); margin: 0 0 1rem; }
    a { color: var(--color-primary); font-size: 0.875rem; }
  `],
})
export class RecuperarComponent {}

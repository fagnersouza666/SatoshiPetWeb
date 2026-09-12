import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Página de verificação de código (placeholder — épico CONTA).
 * Implementada na história CONTA-02.
 */
@Component({
  selector: 'app-verificar',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page" aria-labelledby="verificar-titulo">
      <h1 id="verificar-titulo">Verificar código</h1>
      <p>Confirmação de código de acesso — em breve.</p>
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
export class VerificarComponent {}

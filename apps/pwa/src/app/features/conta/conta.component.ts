import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Página da conta do usuário (placeholder — épico CONTA).
 * Dashboard com saldo, pet e histórico implementados nas histórias CONTA-05..10.
 */
@Component({
  selector: 'app-conta',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page" aria-labelledby="conta-titulo">
      <h1 id="conta-titulo">Minha conta</h1>
      <p>Dashboard de acumulação — em breve.</p>
    </section>
  `,
  styles: [`
    .page { padding: 2rem 0; }
    h1 { font-size: 1.5rem; margin: 0 0 0.5rem; }
    p { color: var(--color-text-muted); }
  `],
})
export class ContaComponent {}

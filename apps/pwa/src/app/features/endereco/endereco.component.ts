import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

/**
 * Página pública de endereço Bitcoin (placeholder — épico BTC).
 * Exibe o pet e histórico de um endereço específico.
 * Implementada nas histórias BTC-01..05.
 */
@Component({
  selector: 'app-endereco',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page" aria-labelledby="endereco-titulo">
      <h1 id="endereco-titulo">Endereço Bitcoin</h1>
      @if (address()) {
        <p class="address" aria-label="Endereço: {{ address() }}">{{ address() }}</p>
      }
      <p>Visualização do pet e histórico — em breve.</p>
    </section>
  `,
  styles: [`
    .page { padding: 2rem 0; }
    h1 { font-size: 1.5rem; margin: 0 0 0.5rem; }
    .address {
      font-family: monospace;
      font-size: 0.875rem;
      background: var(--color-surface);
      border: 1px solid var(--color-border);
      border-radius: 0.375rem;
      padding: 0.5rem 0.75rem;
      word-break: break-all;
      margin: 0 0 1rem;
    }
    p { color: var(--color-text-muted); }
  `],
})
export class EnderecoComponent {
  private readonly route = inject(ActivatedRoute);

  protected readonly address = toSignal(
    this.route.paramMap.pipe(map((p) => p.get('address') ?? '')),
  );
}

import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  HostListener,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiClientService } from '../../core/api-client.service';
import { SessionService } from '../../core/session.service';

/** Emissão explícita e exibição temporária do segredo de recuperação (CC-02). */
@Component({
  selector: 'app-recovery-code-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="recovery" aria-labelledby="recovery-title">
      <h2 id="recovery-title">Código de recuperação</h2>
      <p>
        Recupere sua conta mesmo se perder acesso ao e-mail. Cada emissão substitui o código
        anterior.
      </p>
      <p>
        Guarde o código em um lugar seguro fora deste aplicativo. Ele só fica disponível nesta tela
        até você sair.
      </p>
      <button
        type="button"
        (click)="generate()"
        [disabled]="busy() || !online()"
        [attr.aria-busy]="busy()"
      >
        Gerar código de recuperação
      </button>
      @if (!online()) {
        <p role="status">Conecte-se à internet para gerar um código.</p>
      }
      @if (code(); as currentCode) {
        <pre aria-label="Código de recuperação gerado">{{ currentCode }}</pre>
        <div class="actions">
          <button type="button" (click)="copy()">Copiar código</button>
          <button type="button" (click)="download()">Baixar código</button>
        </div>
      }
      @if (status()) {
        <p role="status" aria-live="polite">{{ status() }}</p>
      }
      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }
    </section>
  `,
  styles: [
    `
      .recovery {
        margin-block: var(--space-6);
        padding: var(--space-4);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-lg);
        background: var(--color-surface);
      }
      h2 {
        margin-top: 0;
        font-size: 1.125rem;
      }
      p {
        font-size: 0.9375rem;
        line-height: 1.5;
      }
      pre {
        white-space: pre-wrap;
        overflow-wrap: anywhere;
        font-family: var(--font-mono);
        user-select: all;
      }
      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-2);
      }
      button {
        min-height: 2.75rem;
        padding: var(--space-3);
        border-radius: var(--radius-md);
        border: 1px solid var(--color-primary);
        color: var(--color-primary);
        background: var(--color-surface);
        font: inherit;
        cursor: pointer;
      }
      button:focus-visible {
        outline: 2px solid var(--color-primary);
        outline-offset: 3px;
      }
      button:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }
      .error {
        color: var(--color-danger);
      }
    `,
  ],
})
export class RecoveryCodePanelComponent {
  private readonly api = inject(ApiClientService);
  private readonly session = inject(SessionService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly secret = signal<{ revision: number; code: string } | null>(null);
  protected readonly code = computed(() => {
    const secret = this.secret();
    return secret?.revision === this.session.revision() ? secret.code : null;
  });
  protected readonly busy = signal(false);
  protected readonly online = signal(navigator.onLine);
  protected readonly error = signal<string | null>(null);
  protected readonly status = signal<string | null>(null);

  constructor() {
    effect(() => {
      this.session.revision();
      this.secret.set(null);
      this.busy.set(false);
      this.error.set(null);
      this.status.set(null);
    });
    this.destroyRef.onDestroy(() => this.secret.set(null));
  }

  @HostListener('window:online')
  @HostListener('window:offline')
  protected updateConnection(): void {
    this.online.set(navigator.onLine);
  }

  protected async generate(): Promise<void> {
    if (this.busy() || !navigator.onLine || !this.session.isAuthenticated()) return;
    const revision = this.session.revision();
    this.busy.set(true);
    this.secret.set(null);
    this.error.set(null);
    this.status.set(null);
    try {
      const response = await firstValueFrom(
        this.api.post<{ code: string }>('/v1/account/recovery/code', {}),
      );
      if (!this.destroyRef.destroyed && revision === this.session.revision()) {
        this.secret.set({ revision, code: response.code });
        this.status.set('Novo código gerado. Guarde-o antes de sair desta tela.');
      }
    } catch {
      if (!this.destroyRef.destroyed && revision === this.session.revision()) {
        this.error.set('Não foi possível obter o código. Tente gerar outro quando houver conexão.');
      }
    } finally {
      if (!this.destroyRef.destroyed && revision === this.session.revision()) this.busy.set(false);
    }
  }

  protected async copy(): Promise<void> {
    const code = this.code();
    if (!code) return;
    const revision = this.session.revision();
    try {
      await navigator.clipboard.writeText(code);
      if (!this.destroyRef.destroyed && revision === this.session.revision())
        this.status.set('Código copiado.');
    } catch {
      if (!this.destroyRef.destroyed && revision === this.session.revision()) {
        this.error.set('Não foi possível copiar. Selecione o código ou baixe o arquivo.');
      }
    }
  }

  protected download(): void {
    const code = this.code();
    if (!code) return;
    const url = URL.createObjectURL(
      new Blob(
        [`Satoshi Pet — código de recuperação\n${code}\nGuarde este arquivo em um lugar seguro.\n`],
        { type: 'text/plain;charset=utf-8' },
      ),
    );
    try {
      const link = document.createElement('a');
      link.href = url;
      link.download = 'satoshi-pet-recuperacao.txt';
      link.click();
    } finally {
      URL.revokeObjectURL(url);
    }
  }
}

import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { OfflineService } from '../offline/offline.service';

/**
 * Shell principal da aplicação.
 *
 * Compõe o layout mobile-first com:
 * - Banner de aviso offline (CA-031)
 * - Cabeçalho fixo com logo e título
 * - Área de conteúdo principal (router-outlet)
 * - Navegação inferior persistente
 *
 * Acessibilidade: WCAG 2.2 AA — landmarks semânticos, aria-labels,
 * foco visível e região de status para leitores de tela.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent {
  protected readonly offlineService = inject(OfflineService);
}

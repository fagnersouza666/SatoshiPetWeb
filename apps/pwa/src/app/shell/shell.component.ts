import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  ViewChild,
  inject,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
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
 * skip link, foco visível e região de status para leitores de tela.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShellComponent implements AfterViewInit {
  protected readonly offlineService = inject(OfflineService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  @ViewChild('mainContent', { static: true }) private mainContent?: ElementRef<HTMLElement>;

  ngAfterViewInit(): void {
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        // Aguarda a renderização da rota para que o foco não seja perdido pela
        // substituição do conteúdo do router-outlet.
        queueMicrotask(() => this.focarConteudoPrincipal());
      });
  }

  protected pularParaConteudo(event: Event): void {
    event.preventDefault();
    this.focarConteudoPrincipal();
  }

  private focarConteudoPrincipal(): void {
    this.mainContent?.nativeElement.focus();
  }
}

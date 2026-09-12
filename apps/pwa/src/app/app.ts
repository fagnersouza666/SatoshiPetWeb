import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Componente raiz da aplicação.
 *
 * Responsável apenas por montar o router-outlet; o layout (header, nav,
 * banner offline) é gerenciado pelo ShellComponent definido nas rotas.
 */
@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  template: '<router-outlet />',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {}

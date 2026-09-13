import { Routes } from '@angular/router';
import { ShellComponent } from './shell/shell.component';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
    children: [
      // Rota raiz redireciona para /entrar
      { path: '', redirectTo: 'entrar', pathMatch: 'full' },

      // ─── Autenticação (épico CONTA) ──────────────────────────────
      {
        path: 'entrar',
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/auth/entrar/entrar.component').then(
                (m) => m.EntrarComponent,
              ),
          },
          {
            path: 'verificar',
            loadComponent: () =>
              import('./features/auth/verificar/verificar.component').then(
                (m) => m.VerificarComponent,
              ),
          },
        ],
      },
      {
        path: 'cadastro',
        loadComponent: () =>
          import('./features/auth/cadastro/cadastro.component').then(
            (m) => m.CadastroComponent,
          ),
      },
      {
        path: 'recuperar',
        loadComponent: () =>
          import('./features/auth/recuperar/recuperar.component').then(
            (m) => m.RecuperarComponent,
          ),
      },

      // ─── Conta autenticada (épico CONTA) — protegida por authGuard ───
      {
        path: 'conta',
        canActivate: [authGuard],
        loadComponent: () =>
          import('./features/conta/conta.component').then(
            (m) => m.ContaComponent,
          ),
      },

      // ─── Endereço público (épico BTC) ────────────────────────────
      {
        path: 'endereco/:address',
        loadComponent: () =>
          import('./features/endereco/endereco.component').then(
            (m) => m.EnderecoComponent,
          ),
      },

      // URLs desconhecidas retornam ao fluxo público de acesso.
      { path: '**', redirectTo: 'entrar' },
    ],
  },
];

import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionService } from '../session.service';

/**
 * Guard de autenticação para rotas privadas.
 *
 * Redireciona para /entrar quando não há sessão ativa.
 * Utilizado nas rotas do épico CONTA que exigem autenticação.
 */
export const authGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);

  if (session.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree(['/entrar']);
};

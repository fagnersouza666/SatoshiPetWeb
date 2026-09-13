import { routes } from './app.routes';
import { ShellComponent } from './shell/shell.component';
import { authGuard } from './core/guards/auth.guard';

describe('rotas standalone da PWA', () => {
  const shellRoute = routes[0];
  const childRoutes = shellRoute.children ?? [];

  const findRoute = (path: string) => childRoutes.find((route) => route.path === path);

  it('deve montar todas as páginas sob o shell standalone', () => {
    expect(shellRoute.path).toBe('');
    expect(shellRoute.component).toBe(ShellComponent);
    expect(shellRoute.children).toBeDefined();
  });

  it('deve redirecionar a raiz para o acesso', () => {
    expect(findRoute('')).toEqual({
      path: '',
      redirectTo: 'entrar',
      pathMatch: 'full',
    });
  });

  it('deve carregar as páginas públicas com loadComponent', () => {
    expect(typeof findRoute('entrar')?.children?.[0]?.loadComponent).toBe('function');
    expect(typeof findRoute('cadastro')?.loadComponent).toBe('function');
    expect(typeof findRoute('recuperar')?.loadComponent).toBe('function');
    expect(typeof findRoute('endereco/:address')?.loadComponent).toBe('function');
  });

  it('deve proteger a página da conta com authGuard', () => {
    expect(findRoute('conta')?.canActivate).toContain(authGuard);
    expect(typeof findRoute('conta')?.loadComponent).toBe('function');
  });

  it('deve manter a verificação do magic-link como filha de /entrar', () => {
    const entrarRoute = findRoute('entrar');

    expect(entrarRoute?.children?.[1]?.path).toBe('verificar');
    expect(typeof entrarRoute?.children?.[1]?.loadComponent).toBe('function');
  });

  it('deve redirecionar URLs desconhecidas para o acesso', () => {
    expect(findRoute('**')).toEqual({
      path: '**',
      redirectTo: 'entrar',
    });
  });
});

import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { ShellComponent } from './shell.component';
import { OfflineService } from '../offline/offline.service';

@Component({
  standalone: true,
  template: '',
})
class TestRouteComponent {}

describe('ShellComponent', () => {
  let fixture: ComponentFixture<ShellComponent>;
  let component: ShellComponent;
  let offlineService: OfflineService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [provideRouter([{ path: 'teste', component: TestRouteComponent }])],
    }).compileComponents();

    offlineService = TestBed.inject(OfflineService);
    fixture = TestBed.createComponent(ShellComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deve ser criado', () => {
    expect(component).toBeTruthy();
  });

  it('deve renderizar o cabeçalho', () => {
    const header = fixture.nativeElement.querySelector('header[role="banner"]');
    expect(header).not.toBeNull();
  });

  it('deve exibir o título "Satoshi Pet" no cabeçalho', () => {
    const titulo = fixture.nativeElement.querySelector('.header__title');
    expect(titulo?.textContent?.trim()).toBe('Satoshi Pet');
  });

  it('deve ter link do logo acessível com aria-label', () => {
    const logo = fixture.nativeElement.querySelector('.header__logo');
    expect(logo?.getAttribute('aria-label')).toBeTruthy();
  });

  it('deve oferecer um link de salto para o conteúdo principal', () => {
    const skipLink = fixture.nativeElement.querySelector('.skip-link');

    expect(skipLink?.getAttribute('href')).toBe('#main-content');
    expect(skipLink?.textContent?.trim()).toBe('Pular para o conteúdo principal');
  });

  it('deve renderizar o elemento main com id main-content', () => {
    const main = fixture.nativeElement.querySelector('main#main-content');
    expect(main).not.toBeNull();
    expect(main?.getAttribute('tabindex')).toBe('-1');
  });

  it('deve mover o foco para o conteúdo principal ao acionar o link de salto', () => {
    const main = fixture.nativeElement.querySelector('main#main-content') as HTMLElement;
    const skipLink = fixture.nativeElement.querySelector('.skip-link') as HTMLElement;
    const focus = vi.spyOn(main, 'focus');

    skipLink.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));

    expect(focus).toHaveBeenCalledOnce();
  });

  it('deve devolver o foco ao conteúdo principal após trocar de rota', async () => {
    const main = fixture.nativeElement.querySelector('main#main-content') as HTMLElement;
    const focus = vi.spyOn(main, 'focus');

    await TestBed.inject(Router).navigateByUrl('/teste');
    await new Promise<void>((resolve) => queueMicrotask(resolve));

    expect(focus).toHaveBeenCalledOnce();
  });

  it('deve renderizar a navegação principal com aria-label', () => {
    const nav = fixture.nativeElement.querySelector('nav[role="navigation"][aria-label]');
    expect(nav).not.toBeNull();
  });

  it('deve ter dois itens de navegação', () => {
    const itens = fixture.nativeElement.querySelectorAll('.bottom-nav__item');
    expect(itens.length).toBe(2);
  });

  it('NÃO deve exibir banner offline quando conectado', () => {
    offlineService.isOffline.set(false);
    fixture.detectChanges();
    const banner = fixture.nativeElement.querySelector('.offline-banner');
    expect(banner).toBeNull();
  });

  it('deve exibir banner offline quando desconectado', () => {
    offlineService.isOffline.set(true);
    fixture.detectChanges();
    const banner = fixture.nativeElement.querySelector('.offline-banner');
    expect(banner).not.toBeNull();
  });

  it('deve ter role="alert" no banner offline', () => {
    offlineService.isOffline.set(true);
    fixture.detectChanges();
    const banner = fixture.nativeElement.querySelector('.offline-banner');
    expect(banner?.getAttribute('role')).toBe('alert');
  });

  it('deve ter aria-live="polite" na região de status', () => {
    const statusRegion = fixture.nativeElement.querySelector('#status-region');
    expect(statusRegion?.getAttribute('aria-live')).toBe('polite');
    expect(statusRegion?.getAttribute('role')).toBe('status');
  });

  it('deve ocultar visualmente a região de status (sr-only)', () => {
    const statusRegion = fixture.nativeElement.querySelector('.sr-only#status-region');
    expect(statusRegion).not.toBeNull();
  });
});

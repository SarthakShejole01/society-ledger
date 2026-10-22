import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';

interface NavLink {
  path: string;
  label: string;
}

/** The frame around every screen after login: who you are, the screen, and the navigation. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="frame">
      <header class="topbar">
        <div>
          <p class="eyebrow">Society Ledger</p>
          <p class="who">{{ name() }}</p>
        </div>
        <button class="btn sm ghost" type="button" (click)="logOut()">Log out</button>
      </header>
      <main><router-outlet /></main>
      <nav aria-label="Main">
        @for (link of links(); track link.path) {
          <a [routerLink]="link.path" routerLinkActive="active">{{ link.label }}</a>
        }
      </nav>
    </div>
  `,
  styles: `
    .frame { max-width: 30rem; margin: 0 auto; min-height: 100dvh; display: flex; flex-direction: column; }
    .topbar { display: flex; justify-content: space-between; align-items: center; gap: 1rem; padding-block: 1rem; }
    .who { margin: 0; font: 700 1rem var(--f-display); }
    main { flex: 1; display: flex; flex-direction: column; gap: 1rem; padding-bottom: 5rem; }
    nav { position: fixed; left: 0; right: 0; bottom: 0; display: flex; justify-content: center; gap: 0.5rem;
          padding: 0.5rem 1rem calc(0.5rem + env(safe-area-inset-bottom, 0px)); background: var(--surface);
          border-top: 1px solid var(--line); }
    nav a { flex: 1; max-width: 10rem; text-align: center; padding: 0.6rem; border-radius: 0.75rem;
            color: var(--muted); text-decoration: none; font-weight: 600; }
    nav a.active { color: var(--brand); border-bottom: 3px solid var(--accent); }
  `,
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly name = computed(() => this.auth.session()?.name ?? '');
  protected readonly links = computed<NavLink[]>(() => {
    const home: NavLink[] = this.auth.session()?.flatId ? [{ path: '/home', label: 'Home' }] : [];
    switch (this.auth.role()) {
      case 'OWNER':
        return [{ path: '/home', label: 'Home' }, { path: '/flats', label: 'Flats' }];
      case 'TREASURER':
        return [...home, { path: '/dashboard', label: 'Dashboard' }, { path: '/record', label: 'Record' }, { path: '/flats', label: 'Flats' }];
      default:
        return [...home, { path: '/dashboard', label: 'Dashboard' }, { path: '/flats', label: 'Flats' }];
    }
  });

  protected logOut(): void {
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}

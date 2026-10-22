import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './models';
import { homeGuard } from './home.guard';

describe('homeGuard', () => {
  function run(role: Role | null, flatId: string | null): boolean | UrlTree {
    const session = role ? { token: 't', role, flatId, name: 'x', societyCode: 'sunrise' } : null;
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { role: signal(role), session: signal(session) } }],
    });
    return TestBed.runInInjectionContext(
      () => homeGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as boolean | UrlTree;
  }

  beforeEach(() => TestBed.resetTestingModule());

  it('lets an owner through, even before their flat is linked, so the screen can explain', () => {
    expect(run('OWNER', null)).toBe(true);
  });

  it('lets a treasurer who owns a flat through', () => {
    expect(run('TREASURER', 'f201')).toBe(true);
  });

  it('lets a committee member who owns a flat through', () => {
    expect(run('COMMITTEE', 'f202')).toBe(true);
  });

  it('sends a treasurer or committee member with no flat to the start', () => {
    const result = run('COMMITTEE', null);

    expect(result instanceof UrlTree).toBe(true);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/');
  });

  it('sends someone with no session to the login page', () => {
    const result = run(null, null);

    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/login');
  });
});

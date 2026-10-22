import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './models';
import { roleGuard } from './role.guard';

describe('roleGuard', () => {
  function run(role: Role | null, ...allowed: Role[]): boolean | UrlTree {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { role: signal(role) } }],
    });
    return TestBed.runInInjectionContext(
      () => roleGuard(...allowed)({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as boolean | UrlTree;
  }

  beforeEach(() => TestBed.resetTestingModule());

  it('lets an allowed role through', () => {
    expect(run('TREASURER', 'TREASURER')).toBe(true);
  });

  it('sends a role that is not allowed back to the start', () => {
    const result = run('OWNER', 'TREASURER', 'COMMITTEE');

    expect(result instanceof UrlTree).toBe(true);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/');
  });

  it('sends someone with no role to the login page', () => {
    const result = run(null, 'TREASURER');

    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/login');
  });
});

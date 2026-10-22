import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './models';

/** Lets only the given roles in. Someone else goes back to the start, and a visitor with no role to the login page. */
export const roleGuard =
  (...allowed: Role[]): CanActivateFn =>
  () => {
    const role = inject(AuthService).role();
    const router = inject(Router);
    if (role === null) {
      return router.createUrlTree(['/login']);
    }
    return allowed.includes(role) ? true : router.createUrlTree(['/']);
  };

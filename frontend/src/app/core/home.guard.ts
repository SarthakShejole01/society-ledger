import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Home shows a flat's own dues. Owners always get it (the screen explains if their login is not linked
 * to a flat yet), and a treasurer or committee member gets it when they own a flat.
 */
export const homeGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const session = auth.session();
  if (!session) {
    return router.createUrlTree(['/login']);
  }
  return session.role === 'OWNER' || session.flatId !== null ? true : router.createUrlTree(['/']);
};

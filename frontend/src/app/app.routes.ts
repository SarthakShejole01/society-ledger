import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { AuthService } from './core/auth.service';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/login/login').then((m) => m.Login) },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shell/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: () => (inject(AuthService).role() === 'OWNER' ? 'home' : 'flats') },
      { path: 'home', loadComponent: () => import('./features/owner/owner-home').then((m) => m.OwnerHome) },
      { path: 'flats', loadComponent: () => import('./features/board/board').then((m) => m.Board) },
    ],
  },
  { path: '**', redirectTo: '' },
];

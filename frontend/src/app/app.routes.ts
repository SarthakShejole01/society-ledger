import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { AuthService } from './core/auth.service';
import { authGuard } from './core/auth.guard';
import { homeGuard } from './core/home.guard';
import { roleGuard } from './core/role.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/login/login').then((m) => m.Login) },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shell/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: () => (inject(AuthService).role() === 'OWNER' ? 'home' : 'dashboard') },
      { path: 'home', canActivate: [homeGuard], loadComponent: () => import('./features/owner/owner-home').then((m) => m.OwnerHome) },
      { path: 'pay-for', canActivate: [roleGuard('OWNER')], loadComponent: () => import('./features/owner/pay-for-another').then((m) => m.PayForAnother) },
      { path: 'dashboard', canActivate: [roleGuard('TREASURER', 'COMMITTEE')], loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.DashboardPage) },
      { path: 'record', canActivate: [roleGuard('TREASURER')], loadComponent: () => import('./features/record/record').then((m) => m.RecordPage) },
      { path: 'flats', loadComponent: () => import('./features/board/board').then((m) => m.Board) },
    ],
  },
  { path: '**', redirectTo: '' },
];

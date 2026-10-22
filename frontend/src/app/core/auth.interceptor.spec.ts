import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let auth: AuthService;
  const navigate = vi.fn();

  beforeEach(() => {
    sessionStorage.clear();
    navigate.mockReset();
    sessionStorage.setItem('society-ledger.session',
      JSON.stringify({ token: 'tok', role: 'OWNER', flatId: 'f1', name: 'Rajesh K', societyCode: 'sunrise' }));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate } },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  it('sends the token with API requests', () => {
    http.get('/api/flats/status').subscribe();

    expect(controller.expectOne('/api/flats/status').request.headers.get('Authorization')).toBe('Bearer tok');
  });

  it('does not send the token to the login endpoints', () => {
    http.post('/api/auth/verify', {}).subscribe();

    expect(controller.expectOne('/api/auth/verify').request.headers.has('Authorization')).toBe(false);
  });

  it('logs out and returns to the login page when the server says the session is over', () => {
    http.get('/api/flats/status').subscribe({ error: () => undefined });

    controller.expectOne('/api/flats/status').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isLoggedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('keeps the session when the server refuses something else', () => {
    http.get('/api/flats/x/dues').subscribe({ error: () => undefined });

    controller.expectOne('/api/flats/x/dues').flush({}, { status: 403, statusText: 'Forbidden' });

    expect(auth.isLoggedIn()).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });
});

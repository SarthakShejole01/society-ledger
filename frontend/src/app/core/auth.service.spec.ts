import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let http: HttpTestingController;

  function create(): AuthService {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('asks for a code with the society code and mobile number', () => {
    const auth = create();

    auth.requestOtp('sunrise', '9876543210').subscribe();

    const req = http.expectOne('/api/auth/otp');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ societyCode: 'sunrise', mobile: '9876543210' });
    req.flush(null, { status: 202, statusText: 'Accepted' });
  });

  it('logs in with the right code and keeps who you are', () => {
    const auth = create();

    auth.verify('sunrise', '9876543210', '482917').subscribe();
    http.expectOne('/api/auth/verify').flush({ token: 'tok', role: 'OWNER', flatId: 'f1', name: 'Rajesh K' });

    expect(auth.isLoggedIn()).toBe(true);
    expect(auth.session()).toEqual({ token: 'tok', role: 'OWNER', flatId: 'f1', name: 'Rajesh K', societyCode: 'sunrise' });
  });

  it('stays logged out when the code is wrong', () => {
    const auth = create();

    auth.verify('sunrise', '9876543210', '000000').subscribe({ error: () => undefined });
    http.expectOne('/api/auth/verify').flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isLoggedIn()).toBe(false);
  });

  it('restores the session after a reload', () => {
    sessionStorage.setItem('society-ledger.session',
      JSON.stringify({ token: 'tok', role: 'TREASURER', flatId: null, name: 'Meena T', societyCode: 'sunrise' }));

    const auth = create();

    expect(auth.session()?.role).toBe('TREASURER');
  });

  it('ignores a damaged stored session', () => {
    sessionStorage.setItem('society-ledger.session', '{not json');

    const auth = create();

    expect(auth.isLoggedIn()).toBe(false);
  });

  it('forgets the session on logout', () => {
    const auth = create();
    auth.verify('sunrise', '9876543210', '482917').subscribe();
    http.expectOne('/api/auth/verify').flush({ token: 'tok', role: 'OWNER', flatId: 'f1', name: 'Rajesh K' });

    auth.logout();

    expect(auth.isLoggedIn()).toBe(false);
    expect(sessionStorage.getItem('society-ledger.session')).toBeNull();
  });

  it('remembers the society code for next time', () => {
    const auth = create();

    auth.verify('sunrise', '9876543210', '482917').subscribe();
    http.expectOne('/api/auth/verify').flush({ token: 'tok', role: 'OWNER', flatId: 'f1', name: 'Rajesh K' });

    expect(auth.rememberedSocietyCode()).toBe('sunrise');
  });
});

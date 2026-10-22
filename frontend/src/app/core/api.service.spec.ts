import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiService } from './api.service';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  it('gets the dues of a flat for a month', () => {
    let result: unknown;
    api.dues('flat-1', '2026-10').subscribe((d) => (result = d));

    const req = http.expectOne((r) => r.url === '/api/flats/flat-1/dues');
    expect(req.request.params.get('month')).toBe('2026-10');
    req.flush({ flatNumber: '304', month: '2026-10', amountPaise: 150000, lateFeePaise: 10000, totalPaise: 160000, paid: false });
    expect(result).toMatchObject({ flatNumber: '304', totalPaise: 160000 });
  });

  it('gets the status board for a month', () => {
    api.board('2026-10').subscribe();

    const req = http.expectOne((r) => r.url === '/api/flats/status');
    expect(req.request.params.get('month')).toBe('2026-10');
    req.flush([]);
  });

  it('starts a payment without ever sending an amount', () => {
    api.startPayment('flat-1', '2026-10').subscribe();

    const req = http.expectOne('/api/payments');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ flatId: 'flat-1', month: '2026-10' });
    req.flush({ paymentId: 'p1', flatId: 'flat-1', month: '2026-10', totalPaise: 160000, lateFeePaise: 10000 });
  });
});
